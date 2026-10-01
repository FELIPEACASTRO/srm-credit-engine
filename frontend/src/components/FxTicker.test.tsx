import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "../api/client";
import { ageLabel, FxTicker } from "./FxTicker";

const base = Date.parse("2026-09-30T12:00:00Z");

describe("ageLabel: idade da vigencia da cotacao (now injetavel)", () => {
  it("agora, minutos e horas", () => {
    expect(ageLabel("2026-09-30T12:00:00Z", base + 30_000)).toBe("agora");
    expect(ageLabel("2026-09-30T12:00:00Z", base + 5 * 60_000)).toBe("há 5 min");
    expect(ageLabel("2026-09-30T12:00:00Z", base + 3 * 3_600_000)).toBe("há 3 h");
  });

  it("avanca com o tempo (nao congela): o mesmo validFrom lido em instantes diferentes muda", () => {
    const vf = "2026-09-30T12:00:00Z";
    expect(ageLabel(vf, base + 60_000)).toBe("há 1 min");
    expect(ageLabel(vf, base + 120_000)).toBe("há 2 min");
  });

  it("nunca negativo se o validFrom for futuro (relogios dessincronizados)", () => {
    expect(ageLabel("2026-09-30T12:10:00Z", base)).toBe("agora");
  });

  it("timestamp malformado vira traco, nunca 'ha NaN h'", () => {
    expect(ageLabel("nao-e-data", base)).toBe("—");
  });
});

// O componente em si estava com cobertura ZERO (so a funcao pura era testada): um timer
// leak ou o ramo de erro quebrado passariam verdes (achado M3 do code review).
describe("FxTicker (componente): estados e limpeza do timer", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  function renderTicker() {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
      <QueryClientProvider client={qc}>
        <FxTicker />
      </QueryClientProvider>,
    );
  }

  it("cotacao vigente: mostra par, taxa e idade", async () => {
    vi.spyOn(apiClient, "getCurrentRate").mockResolvedValue({
      id: 1, base: "USD", quote: "BRL", rate: "5.4321",
      validFrom: new Date().toISOString(),
    });
    renderTicker();
    expect(await screen.findByText("5.4321")).toBeInTheDocument();
    expect(screen.getByText("USD/BRL")).toBeInTheDocument();
    expect(screen.getByText("agora")).toBeInTheDocument();
  });

  it("sem cotacao utilizavel: estado de erro acionavel, nao tela quebrada", async () => {
    vi.spyOn(apiClient, "getCurrentRate").mockRejectedValue(new Error("503"));
    renderTicker();
    await waitFor(
      () => expect(screen.getByText(/sem cotação — registre uma nova/)).toBeInTheDocument(),
      { timeout: 3_500 },
    );
  });

  it("unmount limpa o tique de 30s (sem timer leak)", async () => {
    vi.spyOn(apiClient, "getCurrentRate").mockResolvedValue({
      id: 1, base: "USD", quote: "BRL", rate: "5.4321",
      validFrom: new Date().toISOString(),
    });
    const clearSpy = vi.spyOn(window, "clearInterval");
    const { unmount } = renderTicker();
    await screen.findByText("5.4321");
    unmount();
    expect(clearSpy).toHaveBeenCalled();
  });
});
