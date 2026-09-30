## Por quê

<!-- O problema/requisito que este PR resolve, não só o quê. -->

## O que muda

## Como verifiquei

<!-- Testes rodados, goldens, oráculo (tools/oracle.py). -->

## Risco no caminho do dinheiro? (s/n) e rollback

## Uso de IA neste PR

<!-- O que foi gerado com IA, o que aceitei e o que rejeitei (e por quê). -->

## Checklist do dinheiro

- [ ] Decimal construído de string; nenhum float/double no caminho do dinheiro
- [ ] Arredondamento só via RoundingPolicy, com modo explícito
- [ ] SQL 100% parametrizado
- [ ] Goldens/discriminantes intocados (`git diff --stat`)

## O que um dev júnior aprende lendo este PR
