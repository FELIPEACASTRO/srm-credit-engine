import { describe, expect, it } from "vitest";
import { applyFilterChange, filtersToSearch, searchToFilters } from "./urlFilters";

describe("filtros do extrato na URL (compartilhavel/bookmarkavel)", () => {
  it("ida e volta sem perda", () => {
    const filters = {
      from: "2026-09-01",
      to: "2026-09-30",
      cedenteId: "2",
      paymentCurrency: "USD",
      cursor: "abc123",
    };
    expect(searchToFilters(filtersToSearch(filters))).toEqual(filters);
  });

  it("campos vazios ficam fora da URL", () => {
    expect(filtersToSearch({ paymentCurrency: "BRL" })).toBe("paymentCurrency=BRL");
    expect(searchToFilters("")).toEqual({});
  });

  it("mudar QUALQUER filtro reseta o cursor (pagina 1 do novo conjunto)", () => {
    const current = { paymentCurrency: "USD", cursor: "pagina3" };
    const next = applyFilterChange(current, { cedenteId: "1" });
    expect(next.cursor).toBeUndefined();
    expect(next.paymentCurrency).toBe("USD");
    expect(next.cedenteId).toBe("1");
  });

  it("avancar pagina preserva os filtros e troca so o cursor", () => {
    const current = { paymentCurrency: "USD", cursor: "p1" };
    const next = applyFilterChange(current, { cursor: "p2" });
    expect(next).toEqual({ paymentCurrency: "USD", cursor: "p2" });
  });

  it("data malformada colada na URL e descartada (nao vai ao backend nem quebra o input)", () => {
    expect(searchToFilters("from=lixo&to=2026-01-31&cedenteId=2"))
        .toEqual({ to: "2026-01-31", cedenteId: "2" });
    expect(searchToFilters("from=2026-13-99")).toEqual({ from: "2026-13-99" });
    // (o formato passa; valor impossivel e 400 do backend exibido no extrato — borda dele)
  });
});
