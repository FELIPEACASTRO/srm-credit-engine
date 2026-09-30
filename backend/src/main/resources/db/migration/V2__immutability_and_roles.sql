-- V2: imutabilidade imposta NO BANCO (4.1.4: "alterar uma liquidacao registrada nao e uma
-- operacao do sistema") em duas camadas independentes:
--   1. Trigger append-only: dispara para QUALQUER usuario, inclusive o dono da tabela
--      (REVOKE nao vale para o dono). BEFORE TRUNCATE e por statement, nao por linha.
--   2. Papel app_rw sem UPDATE/DELETE/TRUNCATE: a aplicacao conecta como membro dele,
--      nunca como superusuario/dono.

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_rw') THEN
    CREATE ROLE app_rw NOLOGIN;
  END IF;
END
$$;

CREATE FUNCTION forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS
$$BEGIN RAISE EXCEPTION '% is append-only (% blocked)', TG_TABLE_NAME, TG_OP; END$$;

CREATE TRIGGER st_no_ud  BEFORE UPDATE OR DELETE ON settlements
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER st_no_tr  BEFORE TRUNCATE ON settlements
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

CREATE TRIGGER sr_no_ud  BEFORE UPDATE OR DELETE ON settlement_reversals
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER sr_no_tr  BEFORE TRUNCATE ON settlement_reversals
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

CREATE TRIGGER fx_no_ud  BEFORE UPDATE OR DELETE ON exchange_rates
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER fx_no_tr  BEFORE TRUNCATE ON exchange_rates
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

CREATE TRIGGER br_no_ud  BEFORE UPDATE OR DELETE ON base_rates
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER br_no_tr  BEFORE TRUNCATE ON base_rates
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

GRANT SELECT, INSERT ON settlements, settlement_reversals, exchange_rates, base_rates TO app_rw;
GRANT SELECT, INSERT, UPDATE ON receivables TO app_rw;
GRANT SELECT, INSERT ON cedentes TO app_rw;
GRANT SELECT ON currencies TO app_rw;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO app_rw;
