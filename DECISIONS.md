# DECISIONS — cortes, horas e decisões estruturais

> Mantido vivo desde o dia 0. "Um corte bem justificado vale mais que uma feature a mais mal feita" — o enunciado trata priorização como critério de avaliação, então este arquivo registra **o que** foi cortado, **quando** (em qual gate) e **por quê**, além das horas reais por bloco.

## 1. Nível-alvo e estratégia

Nível-alvo: **Sênior, com os documentos de Staff/Tech Lead** (ADRs, design de escala, proposta EDA, post-mortem do Anexo B). O staff é "parcialmente substitutivo" — aqui o escopo de implementação sênior foi mantido integral e os documentos foram somados; o corte compensatório está na interface (grid enxuto, sem polimento visual).

Stack: **Java 21 + Spring Boot** (tipagem forte é diferencial declarado; `BigDecimal` para o domínio; fluência do autor para a mudança ao vivo). Justificativa completa no README e em `docs/adr/0004-stack.md`.

## 2. Fila de cortes pré-aprovada (registrada no dia 0)

| Grupo | Corte | Economia | Status |
|---|---|---:|---|
| A1 | Polimento visual zero (o painel de simulação fica) | 0,5 h | **Acionado** |
| A2 | Keyset → offset/limit justificado | 0,25 h | Não acionado (keyset entregue) |
| A3 | Banda de sanidade do câmbio → só `rate > 0` + idade | 0,25 h | Não acionado |
| A5 | `expectedAmount`/`price-changed` → fora | 0,25 h | Não acionado |
| A6 | Testes de propriedade → só oráculo diferencial | 0,25 h | **Acionado** (propriedades mínimas; oráculo `tools/oracle.py` cobre o diferencial) |
| A7 | 3º drill depois da entrega | 0,33 h | — |
| A9 | semgrep → grep no CI | 0,25 h | **Acionado** (grep de SQL interpolado e de float no caminho do dinheiro) |
| B2 | C4 L2 simplificado; 1 métrica em vez de 2 | 0,5 h | Não acionado |

**Não são cortes** (o enunciado já dá a escolha): timeout+retry **ou** circuit breaker (escolhido: timeout+retry com backoff — mais simples de demonstrar e suficiente para um feeder assíncrono); "ao menos 1–2 métricas"; endpoint manual **e** mock de provedor (sênior exige o mock, então há os dois).

**Nunca cortar:** decimal fim a fim; goldens + discriminantes + empates; transação, idempotência e imutabilidade; 3 filtros do extrato; painel de simulação; OpenAPI com erros; os 5 `.md`; drills antes da entrega.

## 3. Decisões estruturais (uma linha cada; detalhes em SPEC/ADRs)

- **Monólito modular em 3 camadas** com atalho de 2 camadas só no extrato (`docs/adr/0002`).
- **Lock otimista** (`UPDATE ... WHERE id AND version`) + UNIQUE como defesa em profundidade; a UNIQUE protege a duplicidade, o lock protege a transição de estado OPEN→SETTLED.
- **READ COMMITTED basta**: UPDATE condicional + UNIQUE dão exclusão mútua; SERIALIZABLE exigiria retry sem ganho.
- **Chave de idempotência na própria linha** de `settlements` (+ `request_hash`): um INSERT atômico, sem estado `IN_PROGRESS`. Alternativa rejeitada: tabela separada (só valeria para cachear erros ou compartilhar chaves entre endpoints).
- **Escrita com SQL explícito (`JdbcClient`)** na liquidação: a ordem UPDATE→INSERT importa e o flush do JPA não a garante.
- **Keyset pagination** `(settled_at, id)` sem COUNT exato — estável sob inserção concorrente e usa o mesmo índice do extrato.
- **FK + cópia da taxa** no snapshot: FK dá rastreabilidade; a cópia deixa o registro autocontido e legível sem JOIN.
- **Provedor de câmbio só alimenta a tabela**; a liquidação lê o banco. Queda do provedor = staleness (503), nunca liquidação pela metade.
- **Sem estado global no front**: estado de servidor no TanStack Query, filtros/cursor na URL, formulário local.
- **Estorno sem endpoint**: tabela `settlement_reversals` com schema e documentação; operação de correção é processo, não API.
- **Authn/authz fora do escopo** (declarado): ator via `X-Operator` → `settled_by`; plano real: OIDC no gateway.
- **Migrations Flyway** (`V1__schema.sql`, `V2__immutability_and_roles.sql`, seed): schema versionado, nunca `ddl-auto`.

## 4. Alternativas rejeitadas (e por quê)

- **Comando único cadastra+liquida**: duplo clique criaria 2 recebíveis com 1 liquidação cada — a UNIQUE não dispararia e o teste de concorrência "mesmo recebível" ficaria vazio.
- **Tabela `idempotency_keys` separada**: mais código para defender (conflict target explícito, estado IN_PROGRESS, limpeza).
- **SERIALIZABLE**: retry de serialização obrigatório sem benefício sobre o desenho atual.
- **Pro-rata `(1+i)^(dias/30)`**: potência fracionária reintroduz arredondamento no fator; a convenção real é pergunta ao negócio (SPEC A1).
- **Composição multiplicativa**: contradiz o texto e os goldens (C1 daria 92.819,19).
- **PATCH de recebível com `expectedVersion`**: endpoint que o enunciado não pede; o lock se demonstra na liquidação.
- **H2/SQLite nos testes de integração**: semântica de lock e triggers diferentes do PostgreSQL — integração roda contra PG 17 real (CI).

## 5. Fora do escopo (declarado)

Lote de recebíveis (B6); reliquidação pós-estorno (se necessária: chave `(receivable_id, attempt_no)`); face em USD (B4); EDA implementada (é proposta staff em `docs/eda-liquidacao.md`); autenticação real.

## 6. Esforço real por bloco × peso na rubrica

A implementação foi feita em **sessão única intensiva com IA como par** (processo no [`AI_USAGE.md`](AI_USAGE.md)); as horas abaixo são o esforço-equivalente honesto por bloco, contra o estimado do planejamento. O total equivale à faixa sênior+staff planejada (~25–29 h de trabalho convencional) — **acima do esforço-alvo de 8–16 h do enunciado, por decisão consciente**: o excedente comprou exatamente os itens que pesam nas colunas Domínio (20%) e Operação (10–20%) da rubrica, e está declarado aqui porque "priorização é critério de avaliação, não desculpa".

| Bloco | Estimado | Entregue | Verificação |
|---|---:|---|---|
| Spec + scaffolding (N00–N01) | 2,25 h | ✅ SPEC 2 pág + fila de cortes + AGENTS | PR 1 antes de qualquer código |
| Oráculo + goldens vermelhos (N02) | 0,75 h | ✅ C1–C3 + G4–G8 + empates | Oráculo conferiu cada valor antes do commit |
| Motor de precificação (N03) | 1,75 h | ✅ 34 testes unitários < 1 s | Verde na primeira execução pós-red |
| Persistência + imutabilidade (N04) | 1,5 h | ✅ triggers + `app_rw` testados | PG 17 real embarcado |
| Currency engine (N05) | 1,0 h | ✅ as-of, desempate, staleness, banda | 6 ITs |
| Cadastro + liquidação (N06–N07) | 2,5 h | ✅ matriz de idempotência 6 faces + corrida | 11 ITs, barreira determinística |
| API/erros/OpenAPI (N08) | 1,25 h | ✅ ContractIT + AnnexARegressionIT | replay byte a byte idêntico |
| Extrato (N09) | 0,75 h | ✅ keyset estável sob inserção | 5 ITs |
| Frontend (N10, P3) | 2,5 h | ✅ 15 testes + tsc estrito + build | vitest 4 |
| REVIEW.md (N11) | 1,5 h | ✅ escrito DEPOIS da minha liquidação | — |
| Lock + obs + resiliência + CI (S1–S4) | 3,5 h | ✅ mutante do lock provado 5/5 | FxFeederTest sem sleep |
| Compose + smoke (P1) | 1,0 h | ✅ validado pelo job compose-smoke do CI | sem Docker local (fato da máquina) |
| C4 + docs staff (S5, F1–F4) | 4,75 h | ✅ 9 documentos ancorados no código real | nomes conferidos por grep |
| Artefatos finais + entrega (N13–N14) | 1,75 h | ✅ | suíte completa verde antes da tag |

## 7. Decisões tomadas durante a execução (além do plano)

- **PostgreSQL 17 real EMBARCADO (zonky) nos testes** — a máquina de desenvolvimento não tem Docker (Windows Home); Testcontainers foi rejeitado por depender dele. O embarcado dá PG de verdade (triggers, locks, `(row) < (row)`) local e no CI com a MESMA suíte. Registrado no [ADR-0004](docs/adr/0004-stack.md).
- **`spring-boot:test-run` como demo local sem Docker** — o avaliador usa o compose; drills e demo nesta máquina usam o runner de teste com o embarcado.
- **`Instant` truncado a micros antes de persistir** — `timestamptz` guarda micros; replay tem que ser byte a byte idêntico (caso 1 do AI_USAGE).
- **vitest 4** para alinhar tipos com vite 8 (caso 10 do log de IA).
