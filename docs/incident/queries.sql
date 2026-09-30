-- =============================================================================
-- Anexo B — Queries de evidência e contenção (companheiras do post-mortem)
-- docs/incident/postmortem-anexo-b.md
--
-- Contexto: o schema do INCIDENTE é o do Anexo A (settlements com
-- receivable_id, amount, currency — SEM timestamp, SEM idempotency_key).
-- Onde este repositório já tem coluna melhor (settled_at), a query anota.
-- Regra de condução: TODA query roda primeiro em dry-run (BEGIN ... ROLLBACK),
-- revisada a 4 olhos, antes de qualquer commit.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. H1 — Duplicatas por recebível (a pergunta central: quantos? quais?)
--    Blast radius bruto: recebíveis com mais de uma liquidação registrada.
-- -----------------------------------------------------------------------------
SELECT s.receivable_id,
       count(*)            AS copies,
       min(s.id)           AS first_id,
       max(s.id)           AS last_id,
       sum(s.amount)       AS total_registered
FROM settlements s
GROUP BY s.receivable_id
HAVING count(*) > 1
ORDER BY count(*) DESC, s.receivable_id;

-- 1b. As mesmas duplicatas agregadas por CEDENTE — é assim que a mesa enxerga
--     ("três cedentes receberam duas vezes"): confere com o reporte?
SELECT r.cedente_id,
       count(DISTINCT s.receivable_id) AS receivables_afetados,
       count(*) - count(DISTINCT s.receivable_id) AS pagamentos_em_excesso,
       sum(s.amount)                   AS total_registrado
FROM settlements s
JOIN receivables r ON r.id = s.receivable_id
GROUP BY r.cedente_id
HAVING count(*) > count(DISTINCT s.receivable_id)
ORDER BY pagamentos_em_excesso DESC;

-- -----------------------------------------------------------------------------
-- 2. Intervalo entre as cópias — via id-sequência como proxy de tempo
--    (o INSERT do Anexo A NÃO grava timestamp; a ordem do bigserial é a única
--    cronologia dentro do banco).
--    Leitura: distância de POUCOS ids entre as cópias = inserts quase
--    simultâneos => retry de rede / duplo clique / corrida (INSERT duplo).
--    Distância de MUITOS ids (outras liquidações no meio) = a segunda cópia
--    veio dias depois => o UPDATE de status falhou e foi ENGOLIDO pelo catch,
--    e o recebível continuou OPEN até alguém reliquidar.
--    Se houver settled_at (schema deste repo), troque o proxy pela diferença
--    real: segundos = retry/corrida; dias = falha engolida.
-- -----------------------------------------------------------------------------
WITH pares AS (
    SELECT receivable_id, id,
           lag(id) OVER (PARTITION BY receivable_id ORDER BY id) AS prev_id
    FROM settlements
)
SELECT p.receivable_id,
       p.prev_id, p.id,
       p.id - p.prev_id                          AS gap_ids,
       CASE WHEN p.id - p.prev_id <= 3 THEN 'RETRY/CORRIDA (quase simultâneo)'
            ELSE 'FALHA ENGOLIDA (reliquidação tardia)' END AS leitura
FROM pares p
WHERE p.prev_id IS NOT NULL
ORDER BY p.receivable_id;

-- -----------------------------------------------------------------------------
-- 3. Discriminador pelo STATUS ATUAL do recebível (sub-hipóteses de H1)
--    OPEN  com settlement          => UPDATE falhou/engolido (catch do Anexo A)
--    SETTLED com >1 settlement     => reenvio: o 2º INSERT entrou ANTES de o
--                                     UPDATE da 1ª requisição virar SETTLED
--                                     (ou o UPDATE também falhou na 1ª)
-- -----------------------------------------------------------------------------
SELECT r.id AS receivable_id,
       r.status,
       count(s.id) AS settlements,
       CASE
         WHEN r.status = 'OPEN'    AND count(s.id) >= 1 THEN 'UPDATE engolido: reliquidável AGORA'
         WHEN r.status = 'SETTLED' AND count(s.id) >  1 THEN 'reenvio/corrida antes do UPDATE'
         ELSE 'consistente'
       END AS diagnostico
FROM receivables r
LEFT JOIN settlements s ON s.receivable_id = r.id
GROUP BY r.id, r.status
HAVING (r.status = 'OPEN' AND count(s.id) >= 1)
    OR (r.status = 'SETTLED' AND count(s.id) > 1)
ORDER BY r.id;

-- -----------------------------------------------------------------------------
-- 4. H0 — Candidatos a SQLi (currency e receivableId interpolados no Anexo A)
-- -----------------------------------------------------------------------------
-- 4a. currency fora do enum esperado: qualquer valor != BRL/USD só entra
--     por injeção (o payload legítimo do front não gera outro).
SELECT id, receivable_id, amount, currency
FROM settlements
WHERE currency NOT IN ('BRL', 'USD')
ORDER BY id;

-- 4b. settlements órfãos: receivable_id que não existe em receivables
--     (um payload forjado passa por `WHERE id = ${receivableId}` sem FK).
SELECT s.id, s.receivable_id, s.amount, s.currency
FROM settlements s
LEFT JOIN receivables r ON r.id = s.receivable_id
WHERE r.id IS NULL;

-- 4c. Rajadas: muitos inserts em janela estreita de ids (proxy de tempo).
--     Complementar FORA do banco: logs do LB com aspas, ';', '--' ou 'OR 1=1'
--     no corpo/na URL, e PITR/WAL para datar cada linha com precisão.
SELECT (id / 50) AS bloco_de_50_ids,
       count(*)  AS inserts_no_bloco
FROM settlements
GROUP BY (id / 50)
HAVING count(*) > 20          -- calibrar pelo volume normal da mesa
ORDER BY bloco_de_50_ids;

-- (H2 roda fora deste banco: conciliação remessa de pagamento × settlements 1:1.
--  H3: títulos cadastrados 2x — mesma origem econômica em receivables:)
SELECT cedente_id, face_value, due_date, count(*) AS cadastros,
       array_agg(id ORDER BY id) AS receivable_ids
FROM receivables
GROUP BY cedente_id, face_value, due_date
HAVING count(*) > 1;

-- -----------------------------------------------------------------------------
-- 5. Reconciliação DIÁRIA (fica agendada após o incidente; invariante: 0 linhas)
--    Vira o gate permanente da prevenção sistêmica (§8 do post-mortem),
--    ao lado da métrica settlements{outcome="replayed"}.
-- -----------------------------------------------------------------------------
SELECT 'duplicata'       AS violacao, receivable_id::text AS ref
FROM settlements GROUP BY receivable_id HAVING count(*) > 1
UNION ALL
SELECT 'open_com_settlement', r.id::text
FROM receivables r JOIN settlements s ON s.receivable_id = r.id
WHERE r.status = 'OPEN'
UNION ALL
SELECT 'settled_sem_settlement', r.id::text
FROM receivables r LEFT JOIN settlements s ON s.receivable_id = r.id
WHERE r.status = 'SETTLED' AND s.id IS NULL;
-- Esperado: 0 linhas. Qualquer linha => alerta pager, não e-mail.

-- -----------------------------------------------------------------------------
-- 6. Identificação das CÓPIAS (insumo do índice de contenção e dos estornos)
--    Mantemos a PRIMEIRA liquidação (menor id) como original; rn > 1 = cópia.
--    NUNCA DELETE: o registro é imutável (4.1.4) — correção é estorno.
-- -----------------------------------------------------------------------------
SELECT id AS copy_id, receivable_id, amount, currency
FROM (
    SELECT s.*,
           row_number() OVER (PARTITION BY receivable_id ORDER BY id) AS rn
    FROM settlements s
) x
WHERE rn > 1
ORDER BY receivable_id, id;

-- -----------------------------------------------------------------------------
-- 7. CONTENÇÃO — índice único parcial excluindo as cópias já existentes
--    Pré-condições (na ordem do post-mortem, §5):
--      (1) endpoint JÁ pausado na borda (503) — obrigatório: o catch do
--          Anexo A devolve 200 ok:true mesmo com o INSERT bloqueado, e a
--          injeção via currency contorna proteções pontuais;
--      (2) snapshot feito;
--      (3) lista de ids vinda da query 6.
--    Com o endpoint pausado, CREATE comum DENTRO de transação basta (nada
--    concorre). NÃO usar CONCURRENTLY aqui: fora de transação e, se falhar,
--    deixa índice INVALID (limpeza via DROP INDEX CONCURRENTLY).
--    NÃO excluir as cópias com coluna nova (ex.: WHERE kind='SETTLEMENT'):
--    o DEFAULT da coluna nova se aplica às cópias existentes e quebra tudo.
--    Alternativa equivalente: tabela-guarda settled_receivables(receivable_id
--    PRIMARY KEY) + trigger BEFORE INSERT em settlements.
-- -----------------------------------------------------------------------------
BEGIN;
  CREATE UNIQUE INDEX ux_settlement_once
      ON settlements (receivable_id)
      WHERE id <> ALL ('{9901,9917,9942}'::bigint[]);  -- <== ids da query 6
COMMIT;

-- Obs.: no schema definitivo deste repositório a UNIQUE(receivable_id) é total
-- (sem WHERE), porque as cópias do incidente terão estorno em
-- settlement_reversals e o histórico permanece íntegro e imutável.
