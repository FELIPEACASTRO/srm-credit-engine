-- V4: alinha a tabela de moedas a realidade da persistencia. As colunas de dinheiro sao
-- NUMERIC(15,2); o contrato de API trafega dinheiro como \d+\.\d{2}; o frontend formata 2
-- casas. Logo o sistema so suporta moedas de 2 casas decimais. A V1 permitia minor_units
-- entre 0 e 4 — uma moeda de escala != 2 (ex.: JPY=0, BHD=3) seria arredondada em SILENCIO
-- pelo Postgres ao inserir em NUMERIC(15,2), corrompendo o snapshot imutavel e quebrando o
-- replay byte-a-byte. Aqui a restricao passa a = 2: cadastrar uma moeda incompativel FALHA
-- ALTO na migration, em vez de corromper em runtime. Suportar outras escalas exigiria
-- colunas e contrato proprios — decisao consciente fora do escopo (BRL/USD, ambas 2 casas).
ALTER TABLE currencies DROP CONSTRAINT currencies_minor_units_check;
ALTER TABLE currencies ADD CONSTRAINT currencies_minor_units_check CHECK (minor_units = 2);
