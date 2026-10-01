# Runbook — operação do SRM Credit Engine

Companheiro operacional do [`README.md`](../README.md) (como rodar), da [`SPEC.md`](../SPEC.md) (contrato/erros) e do [post-mortem do Anexo B](incident/postmortem-anexo-b.md) (as queries de contenção estão em [`incident/queries.sql`](incident/queries.sql)). Fecha o quarteto **README · OpenAPI · ADR · runbook**.

Regra de condução herdada do post-mortem: **toda query de escrita roda antes em dry-run (`BEGIN … ROLLBACK`), revisada a quatro olhos.** As tabelas `settlements`, `settlement_reversals`, `exchange_rates` e `base_rates` são **append-only** — correção é estorno, nunca `UPDATE`/`DELETE`.

## 1. Subir, derrubar, resetar

| Ação | Docker (canônico) | Windows (conveniência) | Sem Docker (dev) |
|---|---|---|---|
| Subir | `docker compose up --build` | `srm.bat start` | `cd backend && ./mvnw spring-boot:test-run` + `cd frontend && npm run dev` |
| Derrubar | `docker compose down` | `srm.bat stop` | Ctrl-C nos dois processos |
| Reset total (apaga dados) | `docker compose down -v` | `srm.bat reset` | — (o embarcado é efêmero) |
| Status / logs | `docker compose ps` / `logs -f` | `srm.bat status` / `srm.bat logs` | console dos processos |

Senhas locais e variáveis: [`.env.example`](../.env.example). As migrations rodam como **dono** do schema (`DB_MIGRATION_USER`); a aplicação conecta como `credit_engine_app` (sem `UPDATE/DELETE` nas append-only). Sem essas variáveis fora do teste embarcado, o Flyway **falha alto no boot** — nunca resultado errado.

## 2. Endereços

| O quê | URL |
|---|---|
| Painel da mesa (web/nginx, proxy `/api`) | http://localhost |
| API | http://localhost:8080/api/v1 |
| Swagger/OpenAPI | http://localhost:8080/swagger-ui.html |
| Métricas Prometheus | http://localhost:8080/actuator/prometheus |
| Health agregado | http://localhost:8080/actuator/health |
| **Readiness** (pode receber tráfego?) | http://localhost:8080/actuator/health/readiness |
| **Liveness** (o processo está vivo?) | http://localhost:8080/actuator/health/liveness |

> O actuator **não** fica sob `/api`; é acessado direto na `:8080`. O healthcheck do `docker-compose.yml` usa uma checagem **TCP** na 8080 (imagem JRE sem `curl`/`wget`), não estes endpoints — eles servem um orquestrador (k8s) e o diagnóstico manual abaixo.

## 3. Saúde: readiness ≠ liveness (o que fazer com cada um)

| Estado | Significado | Ação |
|---|---|---|
| liveness **UP**, readiness **UP** | Normal | Nada |
| liveness **UP**, readiness **DOWN** (503) | Processo vivo, mas dependência (banco) fora → a instância **sai do tráfego** sozinha num LB | **Não reinicie a API** (reiniciar não cura o banco). Vá ao §5 (banco) |
| liveness **DOWN** | O próprio processo está quebrado (deadlock/OOM) | Aí sim reiniciar/reciclar ajuda; capture heap/thread dump antes |

`readiness` inclui o componente `db`; `liveness` **não** (configurado em `application.properties`) — reiniciar um processo saudável porque o banco caiu só gera *crash-loop*.

## 4. Sinais para observar (métricas e logs)

- **Métricas de negócio** (`/actuator/prometheus`):
  - `settlements_total{outcome="created"|"replayed", currency}` — liquidações/min **e taxa de replay**. Replay subindo é *esperado* sob retry/duplo clique (a idempotência funcionando); replay **sem** carga de retry correspondente é sinal para investigar.
  - `pricing_engine_seconds` — histograma de latência do motor.
- **Logs estruturados** (ECS; `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` no compose): toda resposta carrega **`X-Request-Id`** — correlacione o erro do usuário ao log pela mesma chave.
- **Gate de reconciliação diária** (invariante: **0 linhas**): a query §5 de [`incident/queries.sql`](incident/queries.sql). Qualquer linha ⇒ *alerta de pager, não e-mail*.

### Painel Grafana (profile opcional `observability`)
As métricas acima ganham um dashboard pronto. Sobe **à parte** do `docker compose up` padrão (que segue só db+api+web):

```bash
docker compose --profile observability up --build
```

| Serviço | URL | Credencial |
|---|---|---|
| **Grafana** (dashboard provisionado) | http://localhost:3000 | `admin` / `admin` (ou `GRAFANA_ADMIN_PASSWORD`) |
| Prometheus (scrape + explorador) | http://localhost:9090 | — |

O dashboard **"SRM Credit Engine — Observabilidade"** já vem provisionado — datasource + JSON **assados na imagem** (`infra/observability/`, mesmo padrão de `infra/db`: sem bind-mount, portável): liquidações/min por outcome e moeda, taxa de replay, p50/p95/p99 do motor, latência HTTP p95 por rota, erros 5xx, pool HikariCP e memória JVM. A latência HTTP p95 depende do histograma habilitado em `application.properties` (`percentiles-histogram.http.server.requests`).

## 5. Incidentes comuns

### 5.1 Liquidação falha com **503 `fx-rate-unavailable`** (cotação velha/ausente)
Cotação vigente ultrapassou `FX_MAX_AGE` (24 h) ou não existe para o par. É o comportamento **projetado**: a liquidação nunca usa cotação stale (câmbio as-of, `SPEC.md` A4). `readiness` continua **UP** (o banco está de pé).
1. **Diagnóstico:** confira a cotação vigente em `GET /api/v1/exchange-rates/current`; veja `valid_from`/idade. Nos logs, filtre pelo `X-Request-Id` da tentativa.
2. **Mitigação (feeder):** se o `MockFxProvider`/feeder está ligado (`APP_FX_FEEDER_ENABLED=true`), ele renova sozinho (grava `source='feeder'`). Confirme que o job está rodando.
3. **Mitigação (manual):** registre a cotação em `POST /api/v1/exchange-rates`. Há **banda de sanidade de ±10%** (anti *fat finger*, com `pg_advisory_xact_lock` transacional). Choque cambial real acima da banda entra com `"override": true` no corpo — **decisão logada**, alçada da mesa (`SPEC.md` A4).
4. Nunca “destrave” liquidando com taxa do cliente: **o cliente jamais informa a taxa**.

### 5.2 Suspeita de **liquidação duplicada**
Por construção não deveria acontecer: `UNIQUE(receivable_id)` + lock otimista (`UPDATE … WHERE id AND version`) + `idempotency_key`/`request_hash`. Duplo clique/retry vira **replay 200**, não segunda liquidação.
1. **Confirme o alcance:** rode as queries §1 e §1b de [`incident/queries.sql`](incident/queries.sql) (duplicatas por recebível e por cedente).
2. **Distinga a causa:** `settlements_total{outcome="replayed"}` alto = idempotência absorvendo retries (saudável). Linha na reconciliação §5 = anomalia real → escalar.
3. **Correção = estorno (processo, não endpoint):** o estorno é um `INSERT` **append-only** em `settlement_reversals` — o papel `app_rw` tem `SELECT`/`INSERT` nas tabelas históricas, mas **não** `UPDATE`/`DELETE`/`TRUNCATE` (V2), então corrigir é sempre *acrescentar*, nunca alterar. Conduza manualmente, em dry-run `BEGIN … ROLLBACK` revisado a quatro olhos. Não existe rota HTTP de alteração/exclusão (`SPEC.md` B8).

### 5.3 `readiness` **DOWN**
Banco inacessível (queda, credencial, rede). A instância sai do tráfego sozinha.
1. `docker compose ps` / `srm.bat status`; `pg_isready -U postgres -d credit_engine`.
2. Logs do serviço `db`. **Não reinicie a API** por isso (§3).
3. Restaurado o banco, `readiness` volta a **UP** e o tráfego retorna automaticamente.

## 6. Mudanças de schema e dados

- **Migrations são aditivas (expand)**: `V1` schema · `V2` imutabilidade+papéis · `V3` seed · `V4` guard de escala de moeda · `V5` coerência do snapshot de liquidação. Nunca `ddl-auto`; nunca editar uma migration já aplicada — some outra `V_n`.
- **`rollback` de binário não desfaz dados**: reverter a imagem da API **não** desfaz uma migration. Planeje expand → migrate → contrair.
- **Réplica ≠ backup**: o volume `db-data` não é backup; uma exclusão lógica se replica. Backup/PITR é processo à parte (fora do escopo do desafio, citado aqui para não virar suposição).

## 7. Segurança operacional

- **Segredos:** nunca no repositório — só em variáveis/`.env` (modelo em [`.env.example`](../.env.example)).
- **Autorização** está declarada fora do escopo (`SPEC.md` B14): o ator vem do header `X-Operator` → `settled_by` (trilha). Em produção: OIDC no gateway. Não exponha o actuator publicamente sem essa camada.
