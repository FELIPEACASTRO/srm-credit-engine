# ADR 0001 — Banco relacional (PostgreSQL 17) vs. NoSQL

## Status

Aceito — implementado em `backend/src/main/resources/db/migration/` (Flyway `V1__schema.sql`, `V2__immutability_and_roles.sql`).

## Contexto

O sistema registra liquidações de recebíveis de um FIDC. Três exigências do enunciado moldam a escolha do armazenamento:

- **§4.1.3 (Persistência e integridade):** "banco relacional (preferencial); liquidações respeitam ACID — nenhuma liquidação fica 'pela metade'", e o endpoint de liquidação deve ser idempotente sob retry e duplo clique.
- **§4.1.4 (Auditabilidade):** registro imutável com valores, câmbio efetivamente usado e timestamps; "alterar uma liquidação registrada não é uma operação do sistema".
- **§11 (Rubrica):** "Domínio do negócio (precisão decimal, ACID, idempotência, auditoria)" pesa 20% em sênior/staff — é o critério individual mais pesado depois da operação.

O ato de liquidar, no código real, é **uma transição de estado + um fato imutável**: `SettlementService.settle()` executa `UPDATE receivables ... WHERE id AND version` (lock otimista) seguido de `INSERT` em `settlements`, na mesma transação curta. As invariantes que impedem duplicidade vivem no banco:

- `ux_settlements_receivable` — no máximo **uma** liquidação por recebível;
- `ux_settlements_idem` — no máximo **uma** liquidação por `Idempotency-Key`;
- `ck_fx` — `fx_rate_id IS NULL` **se e somente se** o pagamento é em BRL;
- coluna `version` em `receivables` — exclusão mútua na transição OPEN→SETTLED;
- triggers `BEFORE UPDATE/DELETE/TRUNCATE` + papel `app_rw` sem esses privilégios (V2) — imutabilidade imposta **pelo banco**, não pela disciplina da aplicação.

## Decisão

**PostgreSQL 17**, com tipos exatos (`NUMERIC(15,2)` dinheiro, `NUMERIC(9,6)` juros, `NUMERIC(15,8)` câmbio — nunca FLOAT/MONEY), constraints declarativas como última linha de defesa e migrations versionadas por Flyway.

Critérios, em ordem de peso:

1. **ACID nativo na granularidade certa.** A dupla escrita UPDATE→INSERT precisa ser atômica. Em um documento único de NoSQL a atomicidade existiria, mas aqui são **duas tabelas com invariantes cruzadas** (status do recebível × existência do settlement) — exatamente o caso em que transação multi-registro deixa de ser opcional.
2. **UNIQUE como defesa que vale mesmo sem a aplicação.** O `catch (DuplicateKeyException)` do `SettlementService` decide replay × conflito **pelo índice violado** (23505). Esse padrão depende de unicidade fortemente consistente — em stores AP (Dynamo, Mongo com read preference relaxada) a unicidade é da aplicação ou é eventual, e duplicidade eventual é o incidente do Anexo B.
3. **Auditoria e imutabilidade com enforcement.** Trigger + revogação de privilégio do `app_rw` tornam o append-only uma propriedade do schema. Em NoSQL isso vira convenção de código.
4. **O extrato analítico (§4.1.6) é relacional por natureza.** Filtro por período, cedente e moeda com SQL nativo (`StatementQueryDao`, keyset por `(settled_at, id)`) usa os índices compostos `ix_st_cedente` / `ix_st_currency` / `ix_st_period` — igualdade → faixa → id.
5. **Precisão decimal de ponta a ponta.** `NUMERIC` do PostgreSQL casa com `BigDecimal` sem passar por binário; em stores JSON o número trafega como double, e o caminho do dinheiro nunca pode tocar float (§12: eliminatório).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| **MongoDB** (documento = settlement autocontido) | O snapshot autocontido nós já temos (colunas copiadas em `settlements`) **sem abrir mão** de FK, UNIQUE e transação multi-tabela. Transações multi-documento existem no Mongo, mas são a exceção do modelo, não o centro dele; índice único + réplica traz semânticas de leitura que precisariam de auditoria própria. |
| **DynamoDB** (escala de escrita gerenciada) | Idempotência via `ConditionExpression` funciona, mas transação entre itens é limitada e cara, `NUMBER` tem 38 dígitos porém sem `NUMERIC(p,s)` declarativo, e o extrato com 3 filtros combináveis exigiria GSIs por combinação ou um segundo store. Resolve um problema (escala) que este case declaradamente não tem. |
| **Event store puro** (liquidação como evento, estado derivado) | Elegante para o ledger, mas o enunciado pede estado consultável (status do recebível, extrato) **e** registro imutável — o desenho atual entrega os dois: `settlements` **é** um event store append-only com schema forte, e `receivables.status` é a projeção síncrona. A versão assíncrona disso está desenhada em [`eda-liquidacao.md`](../eda-liquidacao.md). |

## Consequências

- Positivas: invariantes verificáveis por teste de integração contra PostgreSQL real (suite roda com embedded-postgres 17.5 — ver ADR 0004); `EXPLAIN` do extrato demonstrável; o incidente do Anexo B fica estruturalmente impossível no schema (a UNIQUE já existe no dia 1, não como contenção).
- Negativas: escala vertical de escrita tem teto — um único PostgreSQL não sustenta 1 milhão de tx/min; migrations passam a ser caminho crítico de deploy; o modelo relacional acopla o extrato ao schema transacional (mitigado pelo atalho de 2 camadas do `StatementQueryDao`, que já isola o SQL de leitura).

## Contra-argumento mais forte — e a resposta honesta

**"No cenário de 1 milhão de tx/min, um relacional não aguenta; NoSQL nasceu para isso."**

Verdadeiro para o **agregado de leitura**, e o design de escala assume isso: em [`escala-1m-tx-min.md`](../escala-1m-tx-min.md), o extrato vira **read model eventual** — que pode perfeitamente ser uma projeção em store NoSQL (chave por cedente, sem COUNT exato), alimentada por eventos. O que **não** migra é o ato de liquidar: lá a resposta é **shard relacional por `hash(receivable_id)`**, mantendo `ux_settlements_receivable`, a chave de idempotência e a transação curta **dentro do mesmo shard** — porque a garantia "uma liquidação por recebível" precisa de unicidade fortemente consistente em algum lugar, e é mais barato manter essa propriedade num relacional particionado do que reconstruí-la na aplicação sobre um store eventual. Ou seja: o contra-argumento procede como **evolução do read model**, não como substituição do write model — e essa fronteira já está desenhada.
