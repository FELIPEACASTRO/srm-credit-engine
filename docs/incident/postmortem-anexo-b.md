# Post-mortem — Liquidações duplicadas (Anexo B)

| Campo | Valor |
|---|---|
| **Severidade** | SEV-1 (impacto financeiro direto em terceiros — cedentes pagos em dobro) |
| **Início do incidente** | Sexta-feira, 18h40 — mesa de operações reporta 3 cedentes com a mesma liquidação paga **duas vezes** |
| **Exposição** | Código do Anexo A em produção há **2 semanas** (merge às pressas numa sexta, Anexo A, contexto) |
| **Plantão** | Eu (staff) + 1 dev pleno |
| **Papéis** | **IC e comunicação: eu.** Execução técnica (queries, snapshot, índice): **o pleno**. Regra de 4 olhos: **todo** SQL passa por revisão dos dois e roda primeiro como **dry-run dentro de transação** (`BEGIN … ROLLBACK`) antes do commit. |
| **Postura** | **Sem deploy noturno arriscado.** Endpoint pausado até segunda; a mesa opera em **fluxo manual assistido** (planilha conferida a 4 olhos). Handoff **escrito** ao fim de cada turno. Este documento é **blameless**. |

## 1. Premissa de coerência (exigida pelo B.1: "linha do tempo coerente com o código")

O Anexo A tem um bug de **unidade** que domina qualquer análise: `BASE_RATE = 1.0` e spreads `1.5`/`2.5` são usados como valores **absolutos** na fórmula `face / (1+base+spread)^prazo` (Anexo A, linhas do `const BASE_RATE` e do cálculo de `presentValue`). Uma duplicata de 3 meses sai por `100000/3.5³ =` **R$ 2.332,36** em vez de R$ 92.859,94 (golden C1, §4.3) — **~40× a menos**; um cheque de 2 meses, `25000/4.5² =` R$ 1.234,57 vs. R$ 23.337,77 — **18,9× a menos**. Logo, **toda** liquidação das 2 semanas saiu grosseiramente subavaliada.

Como a queixa da mesa é "**duplicidade**", e não "valores absurdos", adoto a **premissa (i)**: o valor **pago** ao cedente **não sai deste endpoint** — ele apenas **registra** a liquidação; a instrução de pagamento é montada por outro sistema/planilha junto ao administrador/custodiante (coerente com a pergunta 3 do `SPEC.md`: "liquidar aqui = registrar a instrução"). A duplicidade no registro virou pagamento em dobro a jusante. **Premissa alternativa (ii)**: se o valor pago saísse do endpoint, o incidente real seria **subpagamento em massa de ~95–97,5% em toda liquidação desde D-14** — o blast radius e a comunicação mudariam de escala (todos os cedentes, não 3). **Nos dois casos o ledger interno está errado desde D-14**: abro desde já um **incidente paralelo de dados** (valores registrados errados), com remediação própria na §6.

## 2. Linha do tempo (hipotética, coerente com o código)

- **D-14 (sexta):** merge às pressas do endpoint do Anexo A, sem review nem testes; deploy.
- **D-14 → D-1:** liquidações seguem. Retries de rede e duplos cliques da mesa criam **duplicatas silenciosas**: não há `Idempotency-Key`, não há checagem de `status` antes do INSERT, não há UNIQUE — e quando o `UPDATE receivables SET status='SETTLED'` falha, o `catch (e) {}` **engole o erro e responde 200 `ok:true`**, deixando o recebível `OPEN` e reliquidável.
- **D0 18h40:** mesa reporta 3 cedentes pagos em dobro. **Detecção pelo pior canal possível** (§3).
- **D0 +10 min:** declaro **SEV-1**; papéis definidos (IC/comunicação = eu; execução = pleno); canal único de incidente aberto.
- **D0 +20 min:** **endpoint pausado na borda (503 no LB)** + **snapshot do banco** (contenções 1–2, §5).
- **D0 +40 min:** queries de evidência e blast radius (`docs/incident/queries.sql`) discriminam H0–H3.
- **D0 +60 min:** **índice único de contenção** criado; primeira comunicação formal a mesa/gestão/risco.
- **D+1 → D+3:** hotfix com gates (não na madrugada de sexta), estornos compensatórios, conciliação completa das 2 semanas, recuperação de valores com os cedentes (trilha de negócio).

## 3. Detecção — falha nossa, não sorte deles

Quem detectou foi **a mesa**, com **TTD ≈ 2 semanas**. Não é acaso: o `catch` que engole a exceção e responde `200 OK` (anti-padrão listado na §12 do enunciado) fez o monitor de error-rate ficar **"saudável" durante o incidente inteiro**. Sistema que transforma falha em 200 é sistema **inauditável por monitoramento de erro** — a correção disso é item de ação, não nota de rodapé.

## 4. Hipóteses e como discriminá-las com evidência (queries em `queries.sql`)

| # | Hipótese | Discriminador |
|---|---|---|
| **H0** | **SQLi explorado** (`id` e `currency` interpolados na query: `WHERE id = ${receivableId}` e `'${currency}'`) | `currency` fora do enum {BRL, USD}; `created_at` idênticos em rajada; `receivable_id` órfão; aspas/`;`/`--` nos logs do LB |
| **H1** | **Duplicata no banco** (retry/duplo clique/corrida) — *hipótese principal* | `GROUP BY receivable_id HAVING count(*) > 1` (também por cedente). **Sub-discriminador pelo status atual:** recebível `OPEN` com settlement = UPDATE falhou e foi **engolido** (reliquidação dias depois); `SETTLED` com 2 linhas = **reenvio** (segundos entre inserts, INSERT duplo antes do UPDATE) |
| **H2** | **Duplicidade a jusante** (banco correto; remessa de pagamento duplicada) | Conciliação remessa × settlements 1:1 — se settlements está limpo, o incidente é do sistema de pagamento |
| **H3** | **Mesmo título cadastrado 2×** (dois receivables, duas liquidações legítimas) | Agrupamento por cedente + valor + vencimento em `receivables` |

**Causa contribuinte — evidência escassa:** o INSERT do Anexo A **não grava timestamp** (só `receivable_id, amount, currency`), e `log_statement` vem desligado por padrão no PostgreSQL. Fontes alternativas: **ordem da sequência do `id`** como proxy de tempo, logs de acesso do LB (padrão de retry: mesmo POST em < 2 s), e **PITR/WAL** para reconstruir quando cada linha entrou.

## 5. Contenção — nesta ordem

1. **Pausar o endpoint na borda (503 no LB/gateway).** Não é opcional nem "para depois": (a) o `catch` devolve `200 ok:true` **mesmo com o INSERT bloqueado** — qualquer contenção só no banco deixaria a mesa acreditando que liquidou; (b) a **injeção via `currency`** permite contornar proteções pontuais — enquanto o código vulnerável responde, a superfície está aberta.
2. **Snapshot do banco** (backup + label do incidente) **antes** de qualquer escrita, preservando evidência.
3. **Identificar as cópias:** `row_number() OVER (PARTITION BY receivable_id ORDER BY id)` e reter `rn > 1` — a lista de `id`s das duplicatas (query 6).
4. **Índice único de contenção:** `CREATE UNIQUE INDEX ux_settlement_once ON settlements(receivable_id) WHERE id <> ALL('{…ids das cópias…}'::bigint[])`. Com o endpoint **pausado**, um `CREATE INDEX` comum **dentro de transação** basta (nada concorre). `CONCURRENTLY` seria a escolha errada aqui: não roda em transação e, se falhar, deixa um índice `INVALID` para limpar com `DROP INDEX CONCURRENTLY`. **Nunca** excluir as cópias com um predicado sobre coluna **nova** (ex.: `WHERE kind = 'SETTLEMENT'`): o default da coluna se aplicaria às cópias existentes e o CREATE falharia — ou pior, mascararia linhas. Alternativa equivalente: tabela-guarda `settled_receivables(receivable_id PK)` + trigger `BEFORE INSERT`.
5. **Estornos compensatórios, nunca DELETE.** O registro de liquidação é imutável (§4.1.4 do enunciado); a correção contábil é lançamento de estorno referenciando a cópia. A **recuperação dos valores pagos em dobro é trilha de negócio** (mesa + jurídico junto aos 3 cedentes), não SQL.
6. **Comunicação (eu):** mesa (o que mudou no fluxo até segunda), gestão e **risco/compliance** (impacto financeiro + suspeita de SQLi até H0 ser descartada). **Pergunta aberta registrada:** o administrador/custodiante do FIDC precisa ser notificado formalmente? (depende da confirmação da premissa (i) e de obrigação regulatória — risco/compliance decide.)

## 6. Causa raiz em camadas (5 porquês)

1. Por que pagaram em dobro? → Duas liquidações do mesmo recebível registradas (H1).
2. Por quê? → O código não tem **nenhuma invariante**: sem idempotência, sem checagem de status, sem UNIQUE, sem transação — e o `catch` engolindo o UPDATE deixa o recebível reliquidável **com 200 OK**.
3. Por que esse código chegou a produção? → **Merge às pressas numa sexta, sem review e sem testes** (o próprio enunciado o descreve assim).
4. Por que o processo permitiu? → **Nenhum gate automatizado no pipeline**: nada verifica goldens, replay de idempotência, SQL interpolado ou constraints.
5. Por que não havia gates? → **Cultura "a IA gerou, parece ok"**: código gerado por IA tratado como revisado. **A causa raiz é de governança de código gerado por IA, não um dev descuidado** — qualquer um de nós, sob pressão de sexta, mergearia algo parecido sem os gates.

## 7. Correção definitiva — o desenho que este repositório implementa

Cada defeito do Anexo A tem, neste repo, o mecanismo que o **impede por construção**:

| Defeito no Anexo A | Mecanismo real aqui |
|---|---|
| Bug de unidade (1.0/1.5/2.5 absolutos) + `Math.pow` em float | `MonthlyRate`/`BigDecimal` de string, `RoundingPolicy` HALF_EVEN, goldens C1–C3 no CI (`PricingEngine`, `AGENTS.md` regras 1–2) |
| Retry/duplo clique duplica | `Idempotency-Key` UNIQUE + `request_hash` → replay 200 sem re-precificar (`SettlementService.replayOrReject`) |
| Corrida entre duas requisições | Transação curta **UPDATE versionado → INSERT** (ordem importa) + `UNIQUE(receivable_id)` como última linha (`SettlementService.settle`, passo 5) |
| Catch engolindo erro com 200 | Exceções tipadas → `GlobalExceptionHandler` → 409/422/503 com Problem Details; nenhum 200 em falha |
| SQLi por interpolação | 100% SQL parametrizado (`JdbcClient`; gate S1 do `SPEC.md`) |
| Sem timestamp/snapshot | Snapshot autocontido: taxa base, spread, prazo, `fx_rate_id` + cópia da taxa, `settled_at`, `settled_by` (tabela `settlements`, `docs/er.md`) |
| "Alterar o registro" possível | Append-only imposto **no banco**: triggers `forbid_mutation` + papel `app_rw` sem UPDATE/DELETE/TRUNCATE (`V2__immutability_and_roles.sql`); correção só por `settlement_reversals` |

## 8. Prevenção sistêmica — sem burocracia

"**Proibir deploy na sexta**" é a resposta errada: trata o sintoma, pune o time inteiro e não impede o mesmo merge numa terça. O que previne é **gate automatizado por classe de erro**, custo de minutos por PR:

| Classe de erro | Gate (CI, automático) |
|---|---|
| Erro de unidade/precisão | Goldens C1–C3 + discriminantes de arredondamento obrigatórios no CI |
| Duplicidade | Teste de **replay** (mesma chave 2× → 1 linha) e de corrida (2 chaves → 201+409) |
| SQLi | Grep de SQL interpolado no CI (gate S1) |
| Regressão de invariante | Constraint no banco como última linha — testada (imutabilidade + UNIQUEs) |
| Cegueira operacional | Métrica `settlements{outcome="replayed"}` (já emitida por `SettlementMetrics`) com alerta de anomalia + **conciliação diária** cujo invariante é **zero duplicatas** (query 5) |

## 9. Itens de ação

| # | Ação | Dono | Prio | Prazo |
|---|---|---|---|---|
| 1 | Estornos compensatórios das cópias + conciliação das 2 semanas (incidente de dados da §1) | Pleno (exec.) / eu (review) | P0 | D+3 |
| 2 | Hotfix substituindo o endpoint pelo desenho da §7 (ou rollback para o fluxo anterior) com goldens verdes | Pleno | P0 | D+2 |
| 3 | Recuperação dos valores com os 3 cedentes; posição de risco/compliance sobre custodiante | Eu + negócio | P0 | D+2 |
| 4 | Gates da §8 no pipeline (goldens, replay, grep SQL, teste de imutabilidade) | Eu | P1 | D+5 |
| 5 | Alerta sobre `settlements{outcome}` + conciliação diária agendada | Plataforma | P1 | D+7 |
| 6 | Habilitar trilha mínima no PostgreSQL (`log_statement=mod` ou pgAudit) e retenção de logs do LB | Plataforma | P2 | D+10 |
| 7 | Política de código gerado por IA: `AGENTS.md` como contrato + review humano obrigatório em caminho de dinheiro | Eu | P2 | D+10 |
