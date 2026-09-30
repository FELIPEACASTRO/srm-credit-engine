/**
 * Dinheiro no frontend: SEMPRE string no formato canônico do backend ("12345.67").
 * Nenhum valor monetário passa por Number/parseFloat — parsing e formatação são
 * operações de STRING. O tipo branded impede misturar string qualquer com dinheiro.
 */
export type DecimalString = string & { readonly __brand: "DecimalString" };

const CANONICAL = /^\d+\.\d{2}$/;
/** milhar com "." bem-formado (grupos de 3) OU dígitos corridos; decimal "," de 1-2 casas */
const PT_BR = /^(\d{1,3}(?:\.\d{3})*|\d+)(?:,(\d{1,2}))?$/;

/** "100.000,00" | "25000" | "1.234,5" -> DecimalString; lixo -> null (nunca NaN). */
export function parsePtBr(raw: string): DecimalString | null {
  const trimmed = raw.trim();
  const match = PT_BR.exec(trimmed);
  if (!match || !match[1]) {
    return null;
  }
  const intPart = match[1].replaceAll(".", "");
  const frac = (match[2] ?? "").padEnd(2, "0");
  return `${intPart}.${frac}` as DecimalString;
}

/**
 * Exibição pt-BR a partir da string do backend. Valor fora do contrato (escala != 2)
 * é erro de programação: LANÇA — a camada de exibição jamais arredonda dinheiro.
 */
export function formatMoney(amount: string, currency: string): string {
  if (!CANONICAL.test(amount)) {
    throw new Error(`Valor fora do contrato de exibicao (esperado \\d+.\\d{2}): '${amount}'`);
  }
  const [intDigits, frac] = amount.split(".") as [string, string];
  const grouped = intDigits.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  const symbol = new Intl.NumberFormat("pt-BR", { style: "currency", currency })
      .formatToParts(0)
      .find((part) => part.type === "currency")?.value ?? currency;
  return `${symbol} ${grouped},${frac}`;
}

/** Data CIVIL (yyyy-mm-dd) -> dd/mm/yyyy sem passar por Date (fuso deslocaria o dia). */
export function formatDateBr(isoDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (!match) {
    throw new Error(`Data civil invalida: '${isoDate}'`);
  }
  return `${match[3]}/${match[2]}/${match[1]}`;
}
