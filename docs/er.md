# Diagrama ER

Gerado do schema real (migrations `V1__schema.sql` … `V5__settlement_coherence_checks.sql` —
a V5 amarra a coerência do snapshot: dinheiro positivo e trio `fx_*` todo-ou-nada). Convenções:
dinheiro `NUMERIC(15,2)`, juros `NUMERIC(9,6)`, câmbio `NUMERIC(15,8)`; `base_rates`,
`exchange_rates`, `settlements` e `settlement_reversals` são **append-only** (triggers +
papel `app_rw` na V2).

```mermaid
erDiagram
    cedentes ||--o{ receivables : "cede"
    cedentes ||--o{ settlements : "recebe (desnormalizado p/ extrato)"
    receivables ||--o| settlements : "liquida (UNIQUE receivable_id)"
    settlements ||--o| settlement_reversals : "estorna (UNIQUE settlement_id)"
    base_rates ||--o{ settlements : "taxa base usada (FK + copia)"
    exchange_rates ||--o{ settlements : "cambio usado (FK + copia)"
    currencies ||--o{ exchange_rates : "base/quote"
    currencies ||--o{ receivables : "payment_currency"

    currencies {
        char3 code PK
        smallint minor_units "= 2 (V4: dinheiro e NUMERIC(15,2))"
    }
    cedentes {
        bigserial id PK
        text name
        text document UK
    }
    base_rates {
        bigserial id PK
        numeric_9_6 monthly_rate "append-only"
        timestamptz valid_from
        timestamptz created_at
        text created_by
    }
    exchange_rates {
        bigserial id PK
        char3 base FK "USD"
        char3 quote FK "BRL (rate = BRL por 1 USD)"
        numeric_15_8 rate "append-only; > 0"
        timestamptz valid_from
        text source
        timestamptz created_at
        text created_by
    }
    receivables {
        bigserial id PK
        bigint cedente_id FK
        text type "validado pelo StrategyRegistry"
        numeric_15_2 face_value "> 0"
        char3 face_currency "so BRL (B4)"
        char3 payment_currency FK "fixada no cadastro (B17)"
        date due_date "data civil (A1b)"
        text status "OPEN | SETTLED"
        int version "lock otimista"
        uuid creation_key UK "idempotencia do cadastro"
        timestamptz created_at
    }
    settlements {
        bigserial id PK
        bigint receivable_id FK "UNIQUE"
        bigint cedente_id FK
        uuid idempotency_key UK
        char64 request_hash
        text strategy
        numeric_15_2 face_value "snapshot autocontido"
        int term_months
        date pricing_date
        bigint base_rate_id FK
        numeric_9_6 base_rate "copia"
        numeric_9_6 spread
        text rounding_mode
        numeric_15_2 present_value_brl
        numeric_15_2 discount_brl
        char3 payment_currency FK
        numeric_15_2 paid_amount
        bigint fx_rate_id FK "null sse BRL (ck_fx)"
        numeric_15_8 fx_rate "copia"
        timestamptz fx_valid_from
        text settled_by "ator (X-Operator)"
        timestamptz settled_at "Clock da app"
    }
    settlement_reversals {
        bigserial id PK
        bigint settlement_id FK "UNIQUE"
        text reason
        text reversed_by
        timestamptz reversed_at
    }
```

**Índices de leitura** (igualdade → faixa → id; servem filtro e keyset do extrato):
`ix_st_cedente (cedente_id, settled_at DESC, id DESC)` ·
`ix_st_currency (payment_currency, settled_at DESC, id DESC)` ·
`ix_st_period (settled_at DESC, id DESC)` ·
`ix_fx_asof (base, quote, valid_from DESC, created_at DESC, id DESC)`.
