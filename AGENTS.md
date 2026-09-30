# Regras para agentes de IA neste repositório

1. Dinheiro e taxas: só `BigDecimal` construído **de String**. Proibidos `float`, `double`, `Math.pow`, `doubleValue()` no caminho do dinheiro.
2. Divisão: sempre com `MathContext.DECIMAL128`. Arredondamento: só via `RoundingPolicy`, com `RoundingMode` explícito — nunca default de biblioteca.
3. SQL: sempre parametrizado (`JdbcClient`/`NamedParameter`). Interpolação de string em SQL é proibida.
4. `settlements`, `settlement_reversals`, `exchange_rates` e `base_rates` são **append-only**: nunca gerar UPDATE/DELETE para elas, nem endpoints de alteração.
5. Tipo de recebível desconhecido = erro (422). Nunca criar default/fallback de strategy.
6. **Não editar os testes golden, discriminantes (G4–G8) e de empate.** Se falharem, pare e reporte — o erro está na implementação.
7. A ordem de escrita da liquidação é UPDATE versionado → INSERT, na mesma transação. Não reordenar.
8. Plano antes do código; diff mínimo; nada de arquivos ou código não usados.
9. Testes: `./mvnw test` (unit) e `./mvnw verify -Pintegration` (PostgreSQL real). Oráculo: `python -X utf8 tools/oracle.py --face 100000.00 --n 3 --type DUPLICATA`.
10. Commits: conventional commits, em branch — nunca direto na `main`.
