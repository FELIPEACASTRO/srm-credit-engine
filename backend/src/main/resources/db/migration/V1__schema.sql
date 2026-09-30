-- V1: schema do SRM Credit Engine.
-- Dinheiro em NUMERIC(15,2), juros em NUMERIC(9,6), cambio em NUMERIC(15,8) — nunca float.
-- Tabelas com sufixo de historico (base_rates, exchange_rates) e settlements sao append-only
-- (imposto na V2 por trigger + papel).

CREATE TABLE currencies (
  code        char(3) PRIMARY KEY,
  minor_units smallint NOT NULL CHECK (minor_units BETWEEN 0 AND 4)
);

CREATE TABLE cedentes (
  id       bigserial PRIMARY KEY,
  name     text NOT NULL,
  document text NOT NULL UNIQUE
);

CREATE TABLE base_rates (
  id           bigserial PRIMARY KEY,
  monthly_rate numeric(9,6) NOT NULL CHECK (monthly_rate >= 0 AND monthly_rate < 1),
  valid_from   timestamptz NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   text NOT NULL
);
CREATE INDEX ix_base_rates_asof ON base_rates (valid_from DESC, created_at DESC, id DESC);

CREATE TABLE exchange_rates (
  id         bigserial PRIMARY KEY,
  base       char(3) NOT NULL REFERENCES currencies(code),  -- USD
  quote      char(3) NOT NULL REFERENCES currencies(code),  -- BRL (rate = BRL por 1 USD)
  rate       numeric(15,8) NOT NULL CHECK (rate > 0),
  valid_from timestamptz NOT NULL,
  source     text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by text NOT NULL,
  CHECK (base <> quote)
);
-- as-of: igualdade (base, quote) -> faixa (valid_from) -> desempate por criacao mais nova
CREATE INDEX ix_fx_asof ON exchange_rates (base, quote, valid_from DESC, created_at DESC, id DESC);

CREATE TABLE receivables (
  id               bigserial PRIMARY KEY,
  cedente_id       bigint NOT NULL REFERENCES cedentes(id),
  type             text NOT NULL,  -- validado pelo StrategyRegistry: tipo novo = classe, nao migracao
  face_value       numeric(15,2) NOT NULL CHECK (face_value > 0),
  face_currency    char(3) NOT NULL DEFAULT 'BRL' CHECK (face_currency = 'BRL'),  -- premissa B4
  payment_currency char(3) NOT NULL REFERENCES currencies(code),                  -- premissa B17
  due_date         date NOT NULL,  -- data civil, sem fuso (premissa A1b)
  status           text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'SETTLED')),
  version          int NOT NULL DEFAULT 0,   -- lock otimista
  creation_key     uuid NOT NULL UNIQUE,     -- idempotencia do cadastro
  created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_receivables_cedente ON receivables (cedente_id);

-- Snapshot completo e autocontido: reproduz o calculo sem JOIN (FK da rastreabilidade,
-- a copia da o registro imutavel exigido em 4.1.4).
CREATE TABLE settlements (
  id                bigserial PRIMARY KEY,
  receivable_id     bigint NOT NULL REFERENCES receivables(id),
  cedente_id        bigint NOT NULL REFERENCES cedentes(id),  -- desnormalizado para o extrato
  idempotency_key   uuid NOT NULL,
  request_hash      char(64) NOT NULL,
  strategy          text NOT NULL,
  face_value        numeric(15,2) NOT NULL,
  term_months       int NOT NULL,
  pricing_date      date NOT NULL,
  base_rate_id      bigint NOT NULL REFERENCES base_rates(id),
  base_rate         numeric(9,6) NOT NULL,
  spread            numeric(9,6) NOT NULL,
  rounding_mode     text NOT NULL,
  present_value_brl numeric(15,2) NOT NULL,
  discount_brl      numeric(15,2) NOT NULL,
  payment_currency  char(3) NOT NULL REFERENCES currencies(code),
  paid_amount       numeric(15,2) NOT NULL,
  fx_rate_id        bigint REFERENCES exchange_rates(id),
  fx_rate           numeric(15,8),
  fx_valid_from     timestamptz,
  settled_by        text NOT NULL,
  settled_at        timestamptz NOT NULL,  -- vem do Clock da aplicacao
  CONSTRAINT ck_fx CHECK ((payment_currency = 'BRL') = (fx_rate_id IS NULL))
);
CREATE UNIQUE INDEX ux_settlements_receivable ON settlements (receivable_id);
CREATE UNIQUE INDEX ux_settlements_idem       ON settlements (idempotency_key);
-- extrato: igualdade -> faixa -> id (mesmos indices servem o keyset)
CREATE INDEX ix_st_cedente  ON settlements (cedente_id, settled_at DESC, id DESC);
CREATE INDEX ix_st_currency ON settlements (payment_currency, settled_at DESC, id DESC);
CREATE INDEX ix_st_period   ON settlements (settled_at DESC, id DESC);

CREATE TABLE settlement_reversals (
  id            bigserial PRIMARY KEY,
  settlement_id bigint NOT NULL UNIQUE REFERENCES settlements(id),
  reason        text NOT NULL,
  reversed_by   text NOT NULL,
  reversed_at   timestamptz NOT NULL DEFAULT now()
);
