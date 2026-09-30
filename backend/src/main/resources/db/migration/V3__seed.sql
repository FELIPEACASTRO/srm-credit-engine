-- V3: seed da demonstracao ("roda de primeira", secao 10.4 do README).
-- Recebiveis com vencimento em +3/+2 meses reproduzem os goldens C1/C2 no painel sem
-- digitar nada; a cotacao 5,4321 nasce vigente (renove com POST /api/v1/exchange-rates
-- se FX_MAX_AGE expirar).

INSERT INTO currencies (code, minor_units) VALUES ('BRL', 2), ('USD', 2);

INSERT INTO cedentes (name, document) VALUES
  ('Alfa Distribuidora Ltda',   '11222333000181'),
  ('Beta Industria S.A.',       '44555666000172'),
  ('Gama Comercio de Pecas ME', '77888999000163');

INSERT INTO base_rates (monthly_rate, valid_from, created_by)
VALUES (0.010000, now() - interval '1 hour', 'seed');

INSERT INTO exchange_rates (base, quote, rate, valid_from, source, created_by)
VALUES ('USD', 'BRL', 5.43210000, now(), 'seed', 'seed');

INSERT INTO receivables (cedente_id, type, face_value, payment_currency, due_date, creation_key) VALUES
  (1, 'DUPLICATA', 100000.00, 'BRL', (current_date + interval '3 months')::date, gen_random_uuid()),  -- C1
  (2, 'CHEQUE',     25000.00, 'BRL', (current_date + interval '2 months')::date, gen_random_uuid()),  -- C2
  (1, 'DUPLICATA', 100000.00, 'USD', (current_date + interval '3 months')::date, gen_random_uuid()),  -- C3
  (3, 'DUPLICATA',  18262.00, 'USD', (current_date + interval '3 months')::date, gen_random_uuid()),  -- G6
  (2, 'CHEQUE',      1004.00, 'BRL', (current_date + interval '2 months')::date, gen_random_uuid());  -- G7
