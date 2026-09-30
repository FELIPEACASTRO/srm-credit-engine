import { describe, expect, it } from "vitest";
import { formatDateBr, formatMoney, parsePtBr } from "./money";

// Normaliza NBSP/narrow-NBSP que o Intl usa entre simbolo e valor.
const norm = (s: string) => s.replace(/[  ]/g, " ");

describe("parsePtBr: entrada da mesa (virgula decimal, ponto de milhar) -> DecimalString", () => {
  it("converte formato brasileiro completo", () => {
    expect(parsePtBr("100.000,00")).toBe("100000.00");
    expect(parsePtBr("1.234,5")).toBe("1234.50");
    expect(parsePtBr("25000")).toBe("25000.00");
    expect(parsePtBr("0,99")).toBe("0.99");
  });

  it("rejeita lixo com null (nunca NaN, nunca float)", () => {
    expect(parsePtBr("")).toBeNull();
    expect(parsePtBr("1e3")).toBeNull();
    expect(parsePtBr("abc")).toBeNull();
    expect(parsePtBr("12,34,56")).toBeNull();
    expect(parsePtBr("-100,00")).toBeNull();
    expect(parsePtBr("1.23.4,00")).toBeNull();
  });
});

describe("formatMoney: exibicao pt-BR a partir da STRING do backend (sem passar por Number)", () => {
  it("formata BRL e USD com simbolo e agrupamento", () => {
    expect(norm(formatMoney("92859.94", "BRL"))).toBe("R$ 92.859,94");
    expect(norm(formatMoney("17094.67", "USD"))).toBe("US$ 17.094,67");
    expect(norm(formatMoney("0.05", "BRL"))).toBe("R$ 0,05");
    expect(norm(formatMoney("1234567.89", "BRL"))).toBe("R$ 1.234.567,89");
  });

  it("lanca para valor fora do contrato (escala != 2): erro de programacao, nunca arredondar aqui", () => {
    expect(() => formatMoney("2.345", "BRL")).toThrow();
    expect(() => formatMoney("abc", "BRL")).toThrow();
    expect(() => formatMoney("100", "BRL")).toThrow();
  });
});

describe("formatDateBr: data civil sem fuso (new Date('2026-10-01') viraria 30/09 em Sao Paulo)", () => {
  it("nunca desloca o dia", () => {
    expect(formatDateBr("2026-10-01")).toBe("01/10/2026");
    expect(formatDateBr("2026-01-31")).toBe("31/01/2026");
  });
});
