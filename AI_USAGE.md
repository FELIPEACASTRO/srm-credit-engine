# AI_USAGE — engenharia da colaboração com IA

Este projeto foi construído **com IA como par de programação intensivo** (Claude, no Claude Code), sob um processo desenhado para que erro de IA seja detectado por máquina antes de virar código aceito. Este relatório é curado: traz as specs estratégicas, os casos em que a IA errou **com o detector que pegou**, e o que deliberadamente não foi delegado. O log bruto por evento fica fora do repositório, como anotação de trabalho.

## 1. Specs e prompts estratégicos

- **[`AGENTS.md`](AGENTS.md)** — o contrato permanente do agente, escrito por mim ANTES do código: decimal só de string, arredondamento só via `RoundingPolicy`, SQL só parametrizado, tabelas append-only, **goldens intocáveis** ("se falharem, pare e reporte"), ordem UPDATE→INSERT fixada. Cada regra nasceu de um risco concreto; o arquivo é o prompt de sistema de qualquer sessão sobre este repo.
- **[`SPEC.md`](SPEC.md) como prompt-spec** — as premissas (A1–A4, B1–B18) foram passadas à IA rotuladas como *regra de negócio*, e a seção 4.3 do enunciado como *perfil de aferição* (injetado nos testes, nunca hardcoded no motor). Contrato explícito reduziu o "sutilmente incorreto".
- **Oráculo antes de gerar** — [`tools/oracle.py`](tools/oracle.py) (aritmética exata com `Fraction`, independente do motor) e os valores esperados dos testes foram fixados **antes** de qualquer implementação e conferidos caso a caso. Código e oráculo nunca saíram da mesma "mão": é a independência que faz o teste significar algo.
- **Usos delegados declarados**: scaffolding oficial (start.spring.io, create-vite), boilerplate de DTOs/controllers a partir do contrato já decidido, primeira versão dos documentos staff (ADRs/escala/EDA/post-mortem) a partir de roteiro detalhado meu — todos revisados linha a linha antes do commit.

## 2. Casos concretos em que a IA errou — e como o processo detectou

> Todos reproduzíveis no histórico: cada caso tem o commit do teste (vermelho) e o do fix.

**Caso 1 — replay não-idêntico por precisão de timestamp (nanos × micros).**
A primeira implementação da liquidação gravava `Instant` com nanossegundos; o PostgreSQL (`timestamptz`) guarda **micro**ssegundos. A resposta original (da memória) e o replay (lido do banco) diferiam no último dígito de `settledAt` — violando o contrato "corpo byte a byte idêntico".
*Detector:* `ContractIT.fullSettlementFlow` (teste `e7be515`, vermelho antes do endpoint) comparando as duas respostas como string.
*Correção:* truncar o instante a micros **antes de persistir** (`d31e428`) — o snapshot é o que está gravado. Sem o assert de igualdade exata, isso passaria despercebido até algum consumidor comparar payloads.

**Caso 2 — loop infinito de re-render (heap OOM) no hook de simulação.**
A versão inicial do `useSimulation` colocava o objeto `client` nas dependências do efeito. Um chamador que recria o objeto a cada render (como o teste fazia — e como código de app faria sem querer) gerava efeito→setState→render→novo objeto→efeito… até `JavaScript heap out of memory`.
*Detector:* a própria suíte (`cc52555`) — o vitest morreu com OOM, apontando o hook.
*Correção:* cliente em `useRef`; o gatilho do efeito é **apenas** a mudança do input (`cd801a2`). Correção de robustez real, não acomodação do teste.

**Caso 3 — falso verde silencioso na separação unit/integração do Maven.**
O profile `-Pintegration` sobrescrevia `excludedGroups` com elemento vazio — que o merge de configuração do Surefire **ignora**. Resultado: `Tests run: 0` com `BUILD SUCCESS` — os testes de integração simplesmente não rodavam, e o pipeline diria "verde".
*Detector:* leitura do log de build (a disciplina de conferir "quantos testes rodaram", não só o exit code).
*Correção:* filtragem por propriedades Maven (`test.groups`/`test.excludedGroups`), que o profile sobrescreve deterministicamente (`86e1c62`…`f8ce884`). Classe de erro registrada: *verde sem execução é a pior mentira de CI*.

**Caso 4 — o handler genérico engolindo exceções do framework (405 → 500).**
O `@ExceptionHandler(Exception.class)` capturava também `HttpRequestMethodNotSupportedException`: um `PATCH /settlements/{id}` respondia **500** em vez de 405. Ironia direta com o [`REVIEW.md`](REVIEW.md), que condena exatamente o padrão "engolir e responder errado".
*Detector:* `ContractIT.settlementsAreImmutableOverHttp` (esperava 405).
*Correção:* mapeamentos explícitos de 405/rota-inexistente **antes** do catch-all (`d31e428`).

**Critério mutante executado (registro).** Além dos casos espontâneos, rodei mutações deliberadas para provar que a suíte discrimina: removendo `and version = :version` do UPDATE da liquidação, o teste de concorrência ficou vermelho 5/5 **com a mensagem certa** (a perdedora vira `already-settled` — só a UNIQUE — em vez de `version-conflict`), e voltou a verde com a linha restaurada (`7a594bc`). O teste demonstra o *lock*, não a constraint.

### O que os goldens oficiais pegam — e o que só os detectores próprios pegam

| Classe de erro | Goldens C1–C3 pegam? | Detector neste repo |
|---|---|---|
| Unidade da taxa (1.5 absoluto) | **Sim** | Goldens + `MonthlyRate` rejeita ≥ 1 |
| Prazo em dias / composição multiplicativa / par cambial invertido | **Sim** | Goldens (92.819,19 e 504.424,48 falham) |
| Float/double | **Não** (passam em double) | G5/G6 + tabela de empates + guard de grep no CI |
| Half-up / default de biblioteca | **Não** | G5 (23.214,99≠98), G6 |
| Ordem de conversão (PV cru) | **Não** (C3 coincide) | G4 (20.626,38≠37) |
| Arredondamento intermediário | **Não** | G7 (937,25≠24), G8 |
| Vazamento para number na fronteira | **Não** | `ContractIT` (dinheiro como string) + guard do front |
| CRUD de settlements "de brinde" | **Não** | `ImmutabilityIT` + 405 no contrato |
| Tipo desconhecido com default | **Não** | `StrategyRegistry` lança + `AnnexARegressionIT` |
| Adulteração dos próprios goldens | **Não** | `AGENTS.md` + `git diff --stat` a cada aceite |

## 3. O que NÃO foi delegado, e por quê

- **As premissas do SPEC** — são as decisões que serão questionadas na defesa; a IA foi sparring das alternativas, a escolha e a responsabilidade são minhas.
- **O oráculo e os valores esperados dos testes de aferição** — a independência entre quem calcula a expectativa e quem implementa é o fundamento do processo inteiro. `tools/oracle.py` usa `Fraction` (exato) e não importa nada do motor.
- **A ordenação do REVIEW.md** — "qualquer IA lista defeitos" (seção 5 do enunciado); priorizar pelo impacto no negócio da mesa e defender a inversão (bug de unidade acima de SQLi, com premissa de rede declarada) exige o julgamento que o exercício mede.
- **Os cortes do DECISIONS.md** — o que sai do escopo é decisão de engenharia contra a rubrica, com as horas na mesa.
- **A aceitação final de cada diff** — regra operacional: suíte verde → `git diff --stat` (goldens intocados, nada fora do escopo) → leitura do diff com o checklist do dinheiro. Trecho que eu não explicaria de cabeça é reescrito ou removido.
