# ADR 0004 — Stack: Java 21 + Spring Boot 4 + BigDecimal (e PostgreSQL embarcado nos testes)

## Status

Aceito — `backend/pom.xml`: Spring Boot 4.1.1, Java 21, zonky embedded-postgres 2.1.0 com binários PostgreSQL 17.5.

## Contexto

O §4 do enunciado declara o critério: "stack livre, desde que adequada a ambiente financeiro — **tipagem forte e frameworks maduros são diferenciais**". O §12 torna float binário no dinheiro eliminatório a partir de pleno. E a defesa ao vivo (§9) impõe um critério que não aparece em benchmark: **30–40 minutos de mudança não divulgada no próprio código** — a stack precisa ser aquela em que o autor é mais rápido sob pressão.

O requisito numérico concreto que discrimina as opções: os golden cases usam divisão por `(1 + 0,01 + 0,015)³ = 1,025³`. O fator é exato, mas `100000 / 1,076890625` é **dízima periódica** — a divisão decimal precisa de precisão declarada e de um modo de arredondamento explícito, e cada linguagem erra isso de um jeito diferente por default.

## Decisão

**Java 21 + Spring Boot 4 + `BigDecimal`**, com as regras que estão em `AGENTS.md` e no `SPEC.md` §2:

- `BigDecimal` construído **de String** (nunca de double) em todo o caminho do dinheiro;
- divisão sempre com `MathContext.DECIMAL128` (34 dígitos) — sem `MathContext`, `BigDecimal.divide` numa dízima lança `ArithmeticException: Non-terminating decimal expansion` **já no C1**; o tipo falha barulhento em vez de arredondar por conta própria, e isso é uma feature;
- arredondamento só via `RoundingPolicy` injetável (`HALF_EVEN`, escala = casas da moeda, ao final de cada etapa) — nunca default de biblioteca;
- dinheiro como **string** no JSON; tipos do domínio (`Money`, `MonthlyRate`, `FxRate`) impedem misturar taxa com valor no sistema de tipos.

Critérios: (1) tipagem forte é diferencial declarado do §4 — records, sealed types e o compilador cobrindo o refactoring da mudança ao vivo; (2) `BigDecimal` + `NUMERIC` do PostgreSQL formam um caminho decimal sem conversão binária em nenhum ponto; (3) Spring Boot é o framework maduro do §4 para transação declarativa/programática (`TransactionTemplate` na transação curta do `SettlementService`), `JdbcClient` para SQL explícito e OpenAPI; (4) fluência do autor — o critério honesto para o §9.

## Alternativas consideradas

**Python (FastAPI + `decimal`).** Numericamente o mais correto por default: `decimal.Decimal` nativo, `ROUND_HALF_EVEN` é o rounding default do contexto, precisão default 28 dígitos — os goldens passariam sem susto. Rejeitado pelos outros critérios: tipagem é opcional (mypy não impede um `float` entrando por uma rota não anotada — e float no dinheiro é eliminatório, §12), e o ecossistema transacional/OpenAPI exige mais montagem manual que o Spring. Segunda opção honesta.

**TypeScript (NestJS + decimal.js).** Descartado com propósito duplo: o **Anexo A é TypeScript de propósito** — `Number` (IEEE 754) no dinheiro, `toFixed` no INSERT — e a linguagem não tem tipo decimal nativo; tudo depende de disciplina sobre uma biblioteca. E a biblioteca tem defaults perigosos: `decimal.js` vem com `rounding: ROUND_HALF_UP` e `precision: 20`. O detalhe que importa: **essa configuração passa nos três goldens oficiais** (verificado no `SPEC.md` §2: com taxas 1,025/1,035 e face em centavos não existe empate de meio centavo no PV em BRL — o modo de arredondamento não se manifesta em C1–C3) **e erra nos casos discriminantes** G4–G7 da suíte, construídos exatamente para expor half-up × half-even na conversão cambial. Uma stack em que "os testes passam" e o arredondamento está errado é o pior cenário de um sistema financeiro.

**Kotlin/JVM.** Mesmo runtime e mesmo `BigDecimal`; diferença puramente ergonômica. Java 21 (records, pattern matching) fecha a lacuna e é onde o autor é mais rápido na mudança ao vivo.

## PostgreSQL embarcado (zonky) nos testes

Os testes de integração exigem PostgreSQL **real** — a semântica do lock (`UPDATE ... WHERE version`), os triggers de imutabilidade da V2 e o comportamento do `NUMERIC` não existem em H2/SQLite (rejeitados em `DECISIONS.md` §4). O padrão de mercado seria Testcontainers, mas ele **exige Docker no host** — ausente no ambiente de desenvolvimento deste projeto. Decisão: `io.zonky.test:embedded-postgres` 2.1.0 com o BOM de binários **17.5** — a mesma major do banco de produção do compose. O ganho decisivo: a **mesma suíte** (`./mvnw verify -Pintegration`) roda idêntica na máquina de dev sem Docker e no CI — nenhum perfil "teste local com banco fake". Custo aceito: o zonky baixa/extrai o binário na primeira execução e não cobre extensões que exigissem imagem customizada (nenhuma em uso).

## Consequências

- Positivas: erro de precisão vira exceção em tempo de teste, não centavo perdido em produção; o compilador participa da mudança ao vivo; um único fluxo de teste do dev ao CI.
- Negativas: JVM tem footprint maior que Node/Python para um case; `BigDecimal` é verboso — mitigado pelos tipos de domínio que concentram a aritmética em `pricing`; Spring Boot 4 é recente, e a major nova exige atenção a breaking changes de dependências (aceito: projeto novo, sem legado a migrar).

## Contra-argumento mais forte — e a resposta honesta

**"Java para um case de 8–16 h é canhão para mosca; em TypeScript o mesmo escopo sai em metade do tempo e com um único ecossistema para back e front."**

A velocidade inicial é real — e irrelevante para o critério de avaliação dominante. O case não mede tempo até o primeiro endpoint; mede corretude ao centavo (§4.3), domínio de ACID/idempotência (§11, 20%) e a capacidade de alterar o sistema ao vivo sem quebrá-lo (§9, eliminatório). Nos três eixos a JVM paga: o golden que falha por `ArithmeticException` é diagnosticável em segundos, o que falha por default silencioso de `decimal.js` exige suíte discriminante para sequer ser notado — e foi preciso escrevê-la (G4–G7) para provar esse ponto. O ecossistema único do TS, por fim, não se perdeu: o frontend **é** TypeScript; a fronteira entre os dois trafega dinheiro como string justamente para que nenhum `Number` toque o caminho decimal.
