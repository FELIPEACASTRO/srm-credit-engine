# Ambiguidades — alternativas rejeitadas e perguntas adicionais

Complemento do [SPEC.md](../SPEC.md) (que fixa uma premissa por linha para caber em 2 páginas). Aqui ficam as alternativas consideradas e o restante das perguntas de domínio.

## Alternativas consideradas por ambiguidade

| # | Premissa adotada | Alternativas rejeitadas e por quê |
|---|---|---|
| A1 | Meses completos com clamp de fim de mês | (a) `Period.between` ingênuo: de 30/11/2026 a 28/02/2027 daria **2** meses, mas `30/11 + 3 meses` (com clamp) cai em 28/02 — a regra adotada dá **3**, coerente com aritmética de calendário; (b) pro-rata `(1+i)^(dias/30)`: potência fracionária reintroduz arredondamento no fator; (c) dias corridos/30 truncado: subprecifica sistematicamente |
| A3 | HALF_EVEN ao final de cada etapa | (a) "um único arredondamento no fim" literal: não reproduz o C3, que converte o PV **já arredondado**; (b) arredondar mês a mês: muda centavos (Cheque 1.004/2m: 937,25 vs 937,24) e contradiz "apenas no resultado final" |
| A4 | As-of na tabela interna | (a) "latest" do provedor ao vivo: irreproduzível, inauditável e acopla a liquidação à disponibilidade externa (é o defeito do Anexo A); (b) taxa da data de vencimento: exigiria prever cotação futura |
| B2 | Par ordenado {USD, BRL} | Campo `rate` solto: a notação "BRL/USD" do enunciado é ambígua na convenção FX de mercado (leria ~0,184); o par ordenado torna o sentido da conversão decidível por posição, não por convenção |
| B7 | Chave na linha de `settlements` | Tabela separada de idempotência: exige estado IN_PROGRESS, limpeza e conflict target explícito; só vale se for preciso cachear respostas de erro ou compartilhar chaves entre endpoints |
| B8 | Trigger + papel sem privilégio | Só "não ter endpoint": não protege de UPDATE manual no banco; só REVOKE: não vale para o dono da tabela; event sourcing: escopo desproporcional |
| B17 | Moeda fixada no cadastro | Escolhida na liquidação: viraria enum validado + snapshot + `request_hash`; rejeitada porque a §4.2.1 lista a moeda no input do recebível |

## Perguntas adicionais ao negócio (além das 6 do SPEC)

1. **Lastro e registro:** os recebíveis passam por registradora? Como o fundo se protege de **dupla cessão** do mesmo título (o análogo de negócio da liquidação duplicada do Anexo B)? Verificação de lastro conforme RCVM 175 (Anexo II)?
2. **Cheque pré-datado:** juridicamente o cheque é pagável à vista (Lei 7.357/1985, art. 32) — o "vencimento" é convencional. O spread maior do cheque reflete risco de sustação/devolução do emitente? Há política própria de aceitação?
3. **Coobrigação:** a cessão é com ou sem coobrigação do cedente (CC arts. 295–296)? Isso muda o risco e deveria mudar o spread?
4. **Risco além do tipo:** o spread deveria variar por sacado, cedente, rating e concentração, além do tipo do título?
5. **Correção de cotação errada:** se uma taxa manual for digitada errada, a correção é nova linha com o mesmo `valid_from` (desempate por `created_at`)? Quem tem alçada? Liquidações já feitas com a taxa errada: estorno + relançamento?
6. **Estorno:** existe fluxo contábil/regulatório de estorno no FIDC? Prazo e aprovação?
7. **Horário de corte:** existe cut-off diário da mesa (ex.: 16h) após o qual a liquidação vale D+1?
8. **Feriados:** vencimento em dia não útil rola para o próximo útil (convenção *following*)? Qual calendário (ANBIMA)?
9. **Limites:** há limite de exposição por cedente/sacado que a liquidação deveria validar?
10. **Multimoedas do caixa (§1):** o caixa USD é apartado? A liquidação em USD debita qual conta, e há trava de saldo?
