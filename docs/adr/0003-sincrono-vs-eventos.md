# ADR 0003 — Síncrono vs. eventos no ato de liquidar

## Status

Aceito — implementado em `settlement/SettlementService.java`. Evolução assíncrona desenhada (não implementada) em [`eda-liquidacao.md`](../eda-liquidacao.md).

## Contexto

O §4.1.3 define o contrato do endpoint de liquidação: ACID ("nenhuma liquidação fica 'pela metade'") e idempotência sob retry e duplo clique. O §4.1.4 exige que a resposta carregue o registro imutável com a taxa de câmbio **efetivamente usada**. Ou seja: quando o operador recebe `201`, a liquidação **existe**, com valor final conhecido — não "foi aceita para processamento".

A questão de arquitetura é onde colocar a fronteira síncrono/assíncrono. Três candidatos a assíncrono existiam: (a) o ato de liquidar em si (comando → fila → worker); (b) a consulta ao provedor de câmbio; (c) os efeitos a jusante (instrução de pagamento, ledger, extrato, notificação).

## Decisão

**O ato de liquidar é síncrono e transacional; tudo o que não é o ato pode ser assíncrono.**

O desenho real, no `SettlementService.settle()`:

1. **Replay primeiro**: `findByIdempotencyKey` — retry legítimo responde `200` idêntico sem re-precificar (a matriz completa está na §4 do `SPEC.md`).
2. **Todo I/O de resolução fora da transação**: taxa base (`BaseRateRepository.asOf`), câmbio (`ExchangeRateService.current` — lê a **tabela interna**, nunca o provedor), prazo (`TermCalculator`), precificação (`PricingEngine`). Se falta cotação vigente, é `503` **antes** de qualquer escrita.
3. **Transação curta cobrindo só a dupla escrita**: `UPDATE receivables ... WHERE id = ? AND version = ?` → rowcount 0 dispara `StaleReceivableSignal` (rollback e decisão fora da tx) → `INSERT` do snapshot em `settlements`. A ordem UPDATE→INSERT importa e é regra do repositório (`AGENTS.md`, item 7).
4. **Banco como última linha**: `ux_settlements_receivable` e `ux_settlements_idem` decidem, via `DuplicateKeyException`, entre replay e `409` — mesmo que a aplicação erre.

O item (b) já é assíncrono **por construção**: o provedor de câmbio só alimenta `exchange_rates` (feeder com timeout 800 ms + retry com backoff); a queda dele vira staleness (503), nunca uma liquidação pela metade — o desacoplamento temporal onde ele paga, sem tocar na consistência do ato.

O item (c) é a evolução por **transactional outbox**: a mesma transação curta ganharia um segundo INSERT (`settlement_outbox`), e um relay publicaria para consumidores (instrução de pagamento, ledger, projeção do extrato, notificação). O desenho completo, com semântica at-least-once + efeito-uma-vez, está em [`eda-liquidacao.md`](../eda-liquidacao.md) — proposto, não implementado, por decisão de escopo (§12 pune over-engineering; `DECISIONS.md` §5).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| **Comando assíncrono** (`202 Accepted` + fila + worker liquida) | Muda o contrato do §4.1.3 sem necessidade: o operador precisa do valor final e da confirmação; a fila adiciona um estado intermediário (PENDING) que reabre exatamente a janela de duplicidade que a transação curta fecha. Só se justifica quando a escrita não acompanha a carga — cenário tratado em [`escala-1m-tx-min.md`](../escala-1m-tx-min.md) (`202 + GET`). |
| **Event sourcing do agregado** (liquidação = evento, estado derivado) | O par `settlements` (append-only, autocontido) + `receivables.status` já entrega fato imutável + estado consultável com uma fração da complexidade; replay de eventos não é requisito. |
| **Chamar o provedor de câmbio dentro do fluxo** (síncrono na borda externa) | Acopla a disponibilidade da liquidação à do provedor e coloca I/O de rede a caminho da transação. Rejeitado no SPEC (B15): provedor alimenta tabela, liquidação lê banco. |
| **Outbox já nesta entrega** | Sem consumidor real, o outbox é uma tabela morta + um relay para operar — volume sem julgamento (§12). O ponto de inserção está documentado e custa um INSERT quando o primeiro consumidor existir. |

## Consequências

- Positivas: `201` significa liquidado — sem estados intermediários para reconciliar; o teste de concorrência exigido em §6-sênior (duas liquidações simultâneas → 1×201 + 1×409) é direto contra o código real; latência do fluxo é a da transação local.
- Negativas: os efeitos a jusante de hoje acontecem dentro do request ou não acontecem — notificação e ledger não existem ainda, e quando existirem **não** devem entrar na transação (é o gatilho para ativar o outbox); o throughput de escrita é o de um PostgreSQL (teto tratado no doc de escala).

## Contra-argumento mais forte — e a resposta honesta

**"Síncrono é acoplamento temporal: tudo que o fluxo toca precisa estar de pé no instante do request — você construiu o sistema menos resiliente possível."**

O acoplamento temporal existe, mas o inventário dele é curto e deliberado: no instante do request, a liquidação depende de **uma** coisa fora do processo — o PostgreSQL. O provedor de câmbio já está fora (feeder), e nada mais externo participa. E a dependência restante não é acidente: **consistência forte é a exigência do ato de liquidar** — "uma liquidação por recebível" e "nenhuma pela metade" são invariantes que precisam de um ponto de decisão atômico; distribuí-lo no tempo não remove a exigência, só a transforma em saga com compensação. A resposta certa ao acoplamento temporal não é tornar o ato eventual — é **encolher o ato** (a transação cobre 2 statements e nada de I/O externo) e tornar eventual todo o resto, que é exatamente o corte do outbox: o dinheiro a jusante, o ledger, o extrato e a notificação toleram segundos de atraso; a transição OPEN→SETTLED não tolera ambiguidade.
