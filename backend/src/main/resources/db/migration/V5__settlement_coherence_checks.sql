-- B3 (code review): o snapshot de settlements e a fonte de auditoria — o BANCO deve
-- recusar um snapshot incoerente vindo de FORA da aplicacao (a app ja nao produz um).
--   1) positividade do dinheiro (a app valida na borda; aqui e a ultima linha);
--   2) coerencia do trio de cambio: ou os TRES campos fx_* estao preenchidos (pagamento
--      nao-BRL) ou os tres sao nulos — o ck_fx da V1 amarra so o fx_rate_id a moeda.
alter table settlements
  add constraint ck_settlements_money_positive
    check (face_value > 0 and present_value_brl > 0
       and discount_brl >= 0 and paid_amount > 0),
  add constraint ck_settlements_fx_coherent
    check (((fx_rate_id is null) = (fx_rate is null))
       and ((fx_rate_id is null) = (fx_valid_from is null))
       and (fx_rate is null or fx_rate > 0));
