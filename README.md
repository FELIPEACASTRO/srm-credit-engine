# SRM Credit Engine

[![ci](https://github.com/FELIPEACASTRO/srm-credit-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/FELIPEACASTRO/srm-credit-engine/actions/workflows/ci.yml)

Plataforma de cessão de crédito multimoedas (BRL/USD): precifica recebíveis com deságio, liquida com garantias ACID + idempotência e registra tudo em snapshot **imutável** e auditável. Entrega do desafio técnico da SRM Asset, nível **Sênior + documentos de Staff/Tech Lead**.

> Fio condutor da arquitetura: **cada defeito do Anexo A tem aqui um mecanismo nomeado que o impede** — unidade no tipo (`MonthlyRate`), transação curta UPDATE versionado→INSERT, `UNIQUE` de idempotência no banco, câmbio as-of com snapshot, erro jamais engolido, goldens como gate de CI.

## 1. Como rodar

### Um comando (Docker)

```bash
docker compose up --build
```

No **Windows**, um duplo clique resolve: **`srm.bat`** (ou `srm.bat start|stop|reset|status|logs`). Ele detecta o ambiente sozinho — com o Docker Desktop rodando usa o Compose acima; sem Docker, cai para o modo local (JDK 21+ + Node, PostgreSQL embarcado) com o Maven do PATH ou o wrapper do projeto. Nenhum caminho de máquina é assumido. O Compose continua sendo o caminho canônico e multiplataforma; o `.bat` é conveniência.

| Serviço | URL |
|---|---|
| Painel da mesa (web) | http://localhost |
| API | http://localhost:8080 |
| Swagger/OpenAPI | http://localhost:8080/swagger-ui.html |
| Métricas (Prometheus) | http://localhost:8080/actuator/prometheus |
| Health · readiness · liveness | `/actuator/health` · `/actuator/health/readiness` · `/actuator/health/liveness` (:8080) |

Sobe do zero: PostgreSQL 17 (com usuário de aplicação **restrito** — sem UPDATE/DELETE nas tabelas append-only), migrations Flyway + seed, API e web (nginx com proxy `/api`, mesma origem). Reset completo: `docker compose down -v`. Senhas locais: [`.env.example`](.env.example).

**Observabilidade (opcional):** `docker compose --profile observability up --build` sobe também Prometheus e um **Grafana** com o painel *SRM Credit Engine — Observabilidade* já provisionado (http://localhost:3000, `admin`/`admin`) — liquidações por outcome/moeda, replay, p95 do motor e da API, pool e JVM. Passo a passo no [runbook](docs/runbook.md).

### Sem Docker (demo/desenvolvimento local)

```bash
cd backend && ./mvnw spring-boot:test-run     # API + PostgreSQL 17 real EMBARCADO em :8080
cd frontend && npm install && npm run dev      # web em :5173 (proxy /api -> :8080)
```

Pré-requisitos: **JDK 21+** (funciona com 21 ou 22 — o alvo de compilação é 21 LTS) e Node 20+. O Maven vem pelo wrapper.

**Demo de 60 segundos:** abra o painel, deixe o valor `100.000,00`, vencimento hoje+3 meses, Duplicata/BRL → simulação mostra **R$ 92.859,94** (golden C1). Troque a moeda para USD → **US$ 17.094,67** com a cotação semeada 5,4321 (golden C3). Clique "Cadastrar e liquidar" → a liquidação aparece no extrato com totais por moeda. Clique de novo rapidíssimo à vontade: **uma** liquidação (idempotência). A cotação do seed vale por `FX_MAX_AGE` (24 h); renove via `POST /api/v1/exchange-rates` ou deixe o feeder (ligado no compose) renovar. A atualização manual tem banda de sanidade de ±10% contra fat finger — um choque real acima disso entra com `"override": true`. O topo da tela traz um **ticker USD/BRL** com a idade da vigência e um **alternador de tema** claro/escuro; o número líquido é decomposto numa equação auditável (face ÷ taxa ^ prazo = PV → deságio → câmbio).

## 2. Testes e aferição

```bash
cd backend
./mvnw test                     # suíte unitária (goldens C1-C3 + discriminantes G4-G8 + empates) — < 5 s
./mvnw verify -Pintegration     # PostgreSQL 17 REAL embarcado: imutabilidade, idempotência (6 faces),
                                # lock otimista com barreira, contrato HTTP, vetores do Anexo A, extrato
cd frontend && npm test         # money pt-BR, corrida da simulação, chave por intenção, filtros na URL

python -X utf8 tools/oracle.py --face 100000.00 --n 3 --type DUPLICATA --fx 5.4321   # oráculo independente
```

Os goldens oficiais têm pontos cegos verificados (passam com double, half-up, ordem de conversão errada e arredondamento intermediário) — por isso a suíte carrega **casos discriminantes próprios** (G4–G8) e uma tabela de empates construída de string. Detalhe em [`AI_USAGE.md`](AI_USAGE.md) §2.

## 3. Arquitetura e fluxo de uma liquidação

Monólito modular em 3 camadas (4.1.7), com o atalho de duas camadas **só** no extrato ([C4 níveis 1 e 2](docs/c4.md), [ER](docs/er.md)):

```
[SPA React/TS] --HTTP/JSON (dinheiro = STRING)--> [API Spring Boot]
    web/         controllers finos · Problem Details RFC 9457 · OpenAPI
    pricing/     DOMÍNIO PURO: Money, MonthlyRate, RoundingPolicy, Strategy+registry (sem default),
                 DiscountFactor aditivo, FxConversion (par ordenado), TermCalculator (clamp fim de mês)
    fx/          as-of na tabela interna · staleness 503 · feeder (timeout 800ms, 3 tentativas) <- MockFxProvider
    receivable/  cadastro idempotente (creation_key) · simulação NO MESMO motor
    settlement/  transação curta: UPDATE ... WHERE id AND version -> INSERT snapshot
    statement/   SQL nativo parametrizado · keyset (settled_at,id) · totais por moeda
                                   [PostgreSQL 17: settlements/rates APPEND-ONLY (trigger + papel app_rw)]
```

**Fluxo do `POST /receivables/{id}/settlement`** (a ordem importa): replay pela `Idempotency-Key` → leitura do recebível (status + versão) → **fora da transação**: taxa base e câmbio as-of, prazo, precificação → `BEGIN` → `UPDATE receivables ... WHERE id AND version` (rowcount 0 = outra transação venceu: replay se foi a mesma chave, senão 409 `version-conflict`) → `INSERT` do snapshot autocontido (taxa usada + vigência + parâmetros que reproduzem o cálculo) → `COMMIT` → 201. Retry devolve **200 com corpo idêntico**. O provedor de câmbio **nunca** é chamado aqui: queda dele vira staleness (503 + `Retry-After`), jamais liquidação pela metade.

## 4. Por que esta stack

**Java 21 + Spring Boot 4**: tipagem forte é diferencial declarado do desafio; `BigDecimal` de string com `MathContext.DECIMAL128` (1,025³ é dízima — sem MathContext o próprio C1 lançaria `ArithmeticException`); `RoundingMode.HALF_EVEN` explícito. **PostgreSQL 17**: relacional "preferencial" no enunciado, e ACID/auditoria pesam na rubrica. **React+TS estrito**: dinheiro como string nas duas direções; o cálculo mora só no backend. Alternativas e contra-argumentos: [ADR-0004](docs/adr/0004-stack.md).

## 5. Decisões e documentos

| Documento | O que carrega |
|---|---|
| [`SPEC.md`](SPEC.md) | Premissas por ambiguidade (A1–A4, B1–B18), matriz de idempotência, critérios de aceite |
| [`DECISIONS.md`](DECISIONS.md) | Fila de cortes, horas por bloco × rubrica, alternativas rejeitadas |
| [`REVIEW.md`](REVIEW.md) | Anexo A por impacto de negócio (bug de unidade ~40x acima do SQLi, com premissas) |
| [`AI_USAGE.md`](AI_USAGE.md) | 4 erros reais de IA + detector, critério mutante executado, o que não delegei |
| [`docs/runbook.md`](docs/runbook.md) | Operação: subir/derrubar, readiness≠liveness, incidentes (câmbio velho, duplicata), estorno como processo |
| [`docs/adr/`](docs/adr) · [escala 1M tx/min](docs/escala-1m-tx-min.md) · [EDA](docs/eda-liquidacao.md) · [post-mortem Anexo B](docs/incident/postmortem-anexo-b.md) | Bloco staff |

## 6. Git

**GitHub Flow com merge commit (nunca squash)** — preserva os pares *teste vermelho → implementação verde* que o `AI_USAGE.md` cita por SHA. Um desenvolvedor, cinco dias, sem releases paralelas: Git Flow seria burocracia; trunk-based puro colide com "nada direto na main" (a única exceção é o `chore: init`). Plano GitHub Free não tem branch protection em repo privado: o CI é o gate declarado e a disciplina está no histórico — cada PR entra com o *porquê* no merge commit ([template](.github/pull_request_template.md)).

## 7. Frontend sem estado global

Estado de servidor no TanStack Query; **filtros e cursor do extrato na URL** (compartilháveis); formulário local. Nada é compartilhado entre telas — store global precisaria de justificativa que não existe aqui. Duplo clique é travado por ref síncrona e a `Idempotency-Key` nasce **por intenção** (mesma intenção + retry = mesma chave = replay do backend). Dinheiro nunca vira `Number` no cliente — trafega como **string de ponta a ponta** (não existe aritmética monetária no browser; `formatMoney` lança se a escala vier errada); `ErrorBoundary` **por seção** (simulação, extrato, ticker) contém uma falha de render sem derrubar a mesa inteira, e a geração de UUID resiste a contexto não-seguro (acesso por IP da LAN).

## 8. Extrato: consulta e índices

O read-side usa SQL nativo parametrizado com predicados na forma exata dos índices — igualdade → faixa → id:

```sql
ix_st_cedente  (cedente_id, settled_at DESC, id DESC)
ix_st_currency (payment_currency, settled_at DESC, id DESC)
ix_st_period   (settled_at DESC, id DESC)
```

A mesma forma serve o keyset (`(settled_at,id) < (:cursor_ts,:cursor_id)`), que não duplica nem pula itens sob inserção concorrente (testado com inserção no meio da paginação). Para inspecionar o plano no compose:

```bash
docker compose exec db psql -U postgres -d credit_engine -c "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM settlements WHERE cedente_id=1 ORDER BY settled_at DESC, id DESC LIMIT 20;"
```

## 9. CI

Cinco jobs em [`ci.yml`](.github/workflows/ci.yml): `golden-cases` (gate nomeado), integração contra PostgreSQL real, frontend (tsc estrito + testes + lint + build), *guards* (grep de float/`Math.pow`/`toFixed` no caminho do dinheiro e de SQL concatenado) e `compose-smoke` — sobe a stack do zero no runner e **afere o C1 ao centavo pela API**. O badge de status fica no topo deste README.
