# SPEC — Premissas, precisão numérica e critérios de aceite

O enunciado contém ambiguidades propositais. Este documento fixa **uma premissa por ambiguidade** (com o reflexo dela no código) e a **pergunta que eu faria ao negócio** num projeto real. Alternativas consideradas e rejeitadas estão em [`docs/ambiguidades.md`](docs/ambiguidades.md) — compressão, não corte, para caber em 2 páginas. Premissa contestada na defesa tem reflexo apontável no código.

## 1. Premissas

| # | Ambiguidade | Premissa → reflexo no código | Pergunta ao negócio |
|---|---|---|---|
| A1 | Unidade do prazo (fórmula usa "Prazo"; o input é uma data) | Meses completos: maior `k` tal que `data_base + k meses ≤ vencimento`, com clamp de fim de mês → `TermCalculator`; prazo < 1 ou vencido → 422 | Convenção da mesa: 30/360, dias corridos ou úteis? Título com < 1 mês é precificável? |
| A1b | Qual é a data-base | Data da liquidação (America/Sao_Paulo), via `Clock` injetado; na simulação, a data corrente. O prazo usado vai ao snapshot | O preço trava na cessão ou no pagamento? |
| A2 | Origem e valor da taxa base | Parâmetro com vigência: tabela `base_rates` append-only, seed 1,00% a.m.; os golden cases **injetam** 1% (perfil de aferição, nunca constante no motor) | Qual o indexador de referência e quem tem alçada para alterar? |
| A3 | Política de arredondamento (modo, escala, momento) | `RoundingPolicy` injetável: HALF_EVEN, escala = casas da moeda, **ao final de cada etapa** (PV em BRL; depois conversão). A ordem da §4.3 é adotada como regra de produção | O PV intermediário em BRL é o valor contábil oficial? |
| A4 | Qual câmbio vale na liquidação | Cotação vigente mais recente com `valid_from ≤ instante da liquidação`, lida da **tabela interna**; idade máxima `FX_MAX_AGE` (24 h, configurável); sem cotação válida → 503 + `Retry-After`. O cliente **nunca** informa a taxa | A mesa trava cotação entre simulação e liquidação? Qual staleness é aceitável? |
| B1 | Composição das taxas | Aditiva `(1 + base + spread)`, pelo texto e pelos goldens (a multiplicativa daria C1 = 92.819,19) | O real é indexador + spread aditivo ou composição de efetivas (DI + spread é multiplicativo)? |
| B2 | Notação "Câmbio (BRL/USD) 5,4321" | Par ordenado `{base: USD, quote: BRL, rate: BRL por 1 USD}`; conversão BRL→USD **divide** | Cotação única ou bid/ask? |
| B3 | Moeda do deságio | Moeda do título (BRL): `deságio = face − PV_arredondado`, invariante contábil por construção | Deveria haver prêmio de risco cambial no spread? (os goldens mostram que não há) |
| B4 | Direção do cross-currency | Face só em BRL (CHECK no banco); outra combinação → 422 | O fundo compra título com face em USD? Com qual taxa base? |
| B5 | Cedente (o extrato filtra por ele; nada pede cadastro) | Entidade `cedentes` com FK obrigatória + seed; campo no formulário | Cadastro vem de onde (ERP, onboarding)? |
| B6 | "Lote de recebíveis" (§1) vs. fluxo unitário | Ingestão unitária; lote fora do escopo | Importação em lote existe? Atômica ou por item? |
| B7 | Semântica da idempotência | `Idempotency-Key` (UUID) gerada **no cliente, uma por intenção**; UNIQUE no banco; matriz de respostas na §4 abaixo | Existe reliquidação legítima? |
| B8 | "Registro imutável" na prática | `settlements`, `exchange_rates`, `base_rates` append-only: trigger `BEFORE UPDATE/DELETE/TRUNCATE` + papel `app_rw` sem esses privilégios; correção = estorno em `settlement_reversals`, sem endpoint | Existe fluxo regulatório de estorno no FIDC? Quem autoriza? |
| B9 | Simulação × liquidação (a taxa muda entre elas) | Simulação indicativa (mesmo motor, read-only); a liquidação recalcula e aceita `expectedAmount` opcional → 409 `price-changed` se divergir | O operador liquida pelo simulado ou pelo recalculado? Tolerância? |
| B10 | §4.3 diz "taxa informada" | A taxa **nunca** vem do request: é resolvida por vigência; o teste dos goldens semeia 5,4321 | — |
| B11 | Filtro "moeda" do extrato | Moeda de **pagamento**; período filtra `settled_at` | O C3 aparece no filtro BRL, USD ou ambos? |
| B12 | Fuso | UTC no banco (`timestamptz`); período do extrato interpretado em America/Sao_Paulo `[from 00:00, to+1d 00:00)` | — |
| B13 | Bordas não especificadas | Fail-fast 422: face ≤ 0, tipo desconhecido (nunca default), moeda fora do enum, vencido, prazo < 1 | Vencido: rejeitar ou precificar com mora? Liquidação parcial existe? |
| B14 | Autenticação/autorização | Fora do escopo, declarado; ator simulado via header `X-Operator` → `settled_by` na trilha. Plano real: OIDC no gateway | Perfis e segregação de função? |
| B15 | Provedor de câmbio caindo | O provedor **só alimenta a tabela** (feeder com timeout 800 ms + 3 tentativas com backoff/jitter); a liquidação lê o banco. Queda vira staleness (503), nunca liquidação pela metade | Fonte oficial da cotação e frequência? |
| B16 | Forma da API de liquidação | Sub-recurso: `POST /receivables/{id}/settlement`; cadastro e liquidação separados (comando único duplicaria recebíveis no duplo clique) | — |
| B17 | Moeda de pagamento: cadastro ou liquidação? | Fixada no **cadastro** do recebível (§4.2.1 a lista no input do recebível); divergência no body → 422 | A mesa decide a moeda só na hora de liquidar? |
| B18 | "Valor líquido" do painel | PV na moeda de pagamento, sem tarifas/impostos | Há tarifas ou tributos a exibir? |

**Tensão de redação da §4.3:** "arredondamento apenas no resultado final" convive com "converte-se o valor presente já arredondado" (dois arredondamentos). Leitura adotada: *resultado final de cada etapa* — precifica em BRL → arredonda; converte → arredonda. É a única leitura que reproduz C3.

## 2. Precisão numérica

- **Banco:** `NUMERIC(15,2)` para dinheiro, `NUMERIC(9,6)` para juros, `NUMERIC(15,8)` para câmbio. Nunca FLOAT/REAL/MONEY.
- **Aplicação:** `BigDecimal` construído **de string**; divisão com `MathContext.DECIMAL128` (34 dígitos — sem ela, `divide` lança `ArithmeticException` já no C1, que é dízima); um único `setScale` por etapa, com `RoundingMode` explícito.
- **Fronteiras:** dinheiro trafega como **string** no JSON (`"92859.94"`); entrada validada por pattern; nunca `float` no caminho do dinheiro (lint + testes de contrato).
- **Quantize antes de persistir; nunca `round()` no SQL** (o `round(numeric)` do PostgreSQL desempata para longe do zero).
- Fato verificado: com taxas 1,025/1,035 e face em centavos **não existe empate de meio centavo no PV em BRL** — o modo de arredondamento só se manifesta na conversão cambial. Por isso a suíte tem casos discriminantes próprios (G4–G7) além dos goldens oficiais.

## 3. Perguntas ao negócio (as 6 mais valiosas)

1. Convenção de prazo (30/360, corridos, úteis) e momento em que o preço trava (cessão × pagamento)?
2. Indexador da taxa base e alçada de alteração?
3. Quem instrui e quem executa o pagamento (administrador/custodiante)? "Liquidar" aqui = registrar a instrução?
4. Câmbio: PTAX (de qual data/lado) ou taxa contratada no fechamento? Custos/tributos e prazo (D+)?
5. Pagamento em USD de título em BRL: cedente não residente ou operação de comércio exterior (Lei 14.286/2021)?
6. Spread só por tipo, ou também por sacado/cedente/concentração? Coobrigação do cedente e registro (dupla cessão)?

Mais perguntas (lastro RCVM 175, cheque à vista, correção de cotação): [`docs/ambiguidades.md`](docs/ambiguidades.md).

## 4. Matriz de respostas da liquidação

| Situação | Resposta |
|---|---|
| Chave nova, recebível OPEN | **201** + snapshot completo |
| Mesma chave, mesmo payload (retry/duplo clique) | **200**, corpo idêntico, `Idempotent-Replayed: true`, **sem re-precificar** |
| Mesma chave, payload diferente | **422** `idempotency-key-reuse` |
| Chave diferente, recebível já liquidado | **409** `already-settled` |
| Corrida de duas chaves pelo mesmo recebível | 1×**201** + 1×**409** `version-conflict` |
| `expectedAmount` diverge do recálculo | **409** `price-changed` |
| Sem `Idempotency-Key` / recebível inexistente | **400** / **404** |
| Input inválido (B13) | **422** com erros por campo |
| Sem cotação vigente ou cotação velha | **503** `fx-rate-unavailable` + `Retry-After` |

## 5. Critérios de aceite (premissas minhas; cada um com verificador)

**Usabilidade** — U1: resultado da simulação ≤ 500 ms após parar de digitar (debounce 300 ms; teste de hook). U2: duplo clique = 1 POST e 1 liquidação (teste de componente). U3: erro 422 aparece no campo, na linguagem da mesa. U4: todo valor exibido com moeda; câmbio com vigência. U5: `label` em todo campo, `aria-live` no resultado, fluxo completo por teclado.

**Segurança** — S1: 100% das queries parametrizadas (grep no CI). S2: validação de schema na borda → 422 sem eco do input. S3: sem rota de alteração de liquidação; `app_rw` sem UPDATE/DELETE/TRUNCATE (teste de imutabilidade). S4: resposta de erro sem stack trace nem SQL. S5: vetores do Anexo A → 4xx e zero linhas alteradas (teste de regressão). S6: nenhum segredo no repositório (`.env.example`). S7: banda de sanidade na atualização manual de câmbio. S8: `Idempotency-Key` obrigatória, gerada no cliente por intenção.

**Desempenho** (local, método documentado no README) — D1: `/simulations` p95 < 200 ms. D2: extrato com index scan no `EXPLAIN (ANALYZE, BUFFERS)`. D3: suíte unitária < 5 s.
