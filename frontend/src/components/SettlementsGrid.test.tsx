import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "../api/client";
import type { SettlementResponse, StatementPage } from "../api/types";
import { SettlementsGrid } from "./SettlementsGrid";

function settlement(id: number, currency: "BRL" | "USD", paid: string): SettlementResponse {
  return {
    id, receivableId: id, cedenteId: 1, strategy: "DUPLICATA", faceValue: "100000.00",
    termMonths: 3, pricingDate: "2026-09-30", baseRate: "0.010000", spread: "0.015000",
    roundingMode: "HALF_EVEN",
    presentValue: { amount: "92859.94", currency: "BRL" },
    discount: { amount: "7140.06", currency: "BRL" },
    paid: { amount: paid, currency },
    fx: currency === "USD" ? { id: 1, rate: "5.43210000", validFrom: "2026-09-30T12:00:00Z" } : null,
    settledBy: "mesa", settledAt: "2026-09-30T12:00:00Z",
  } satisfies SettlementResponse;
}

const PAGE_ALL: StatementPage = {
  items: [settlement(2, "USD", "17094.67"), settlement(1, "BRL", "92859.94")],
  nextCursor: null,
  totalsByCurrency: { BRL: "92859.94", USD: "17094.67" },
};

const PAGE_USD: StatementPage = {
  items: [settlement(2, "USD", "17094.67")],
  nextCursor: null,
  totalsByCurrency: { USD: "17094.67" },
};

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("SettlementsGrid: extrato server-side com filtros na URL", () => {
  beforeEach(() => {
    window.history.replaceState(null, "", "/");
  });
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("renderiza as linhas do backend e um total POR MOEDA (nunca somando moedas)", async () => {
    vi.spyOn(apiClient, "getStatement").mockResolvedValue(PAGE_ALL);
    render(<SettlementsGrid />, { wrapper });

    // as duas liquidacoes aparecem NA TABELA (escopado: o valor tambem vive no rodape de totais)
    const table = await screen.findByRole("table");
    await waitFor(() => within(table).getByText("US$ 17.094,67"));
    expect(within(table).getByText("R$ 92.859,94")).toBeInTheDocument();

    // dois totais distintos, um por moeda — a moeda nunca e agregada junto
    const totalBrl = screen.getByText(/Total BRL/).textContent ?? "";
    const totalUsd = screen.getByText(/Total USD/).textContent ?? "";
    expect(totalBrl).toContain("R$ 92.859,94");
    expect(totalUsd).toContain("US$ 17.094,67");
  });

  it("mudar o filtro de moeda reflete na URL e refaz a busca com o filtro", async () => {
    const spy = vi.spyOn(apiClient, "getStatement")
        .mockResolvedValueOnce(PAGE_ALL)
        .mockResolvedValue(PAGE_USD);
    render(<SettlementsGrid />, { wrapper });
    const table = await screen.findByRole("table");
    await waitFor(() => within(table).getByText("R$ 92.859,94"));

    await userEvent.selectOptions(screen.getByLabelText("Moeda"), "USD");

    // filtro entrou na URL (compartilhavel) ...
    await waitFor(() => expect(window.location.search).toContain("paymentCurrency=USD"));
    // ... e a nova busca levou o filtro ao backend
    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith(expect.stringContaining("paymentCurrency=USD")));
    // a linha BRL sai da tabela no conjunto filtrado
    await waitFor(() => expect(within(table).queryByText("R$ 92.859,94")).not.toBeInTheDocument());
  });

  it("estado vazio e um convite a agir, nao uma tela morta", async () => {
    vi.spyOn(apiClient, "getStatement").mockResolvedValue(
        { items: [], nextCursor: null, totalsByCurrency: {} });
    render(<SettlementsGrid />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(/Nenhuma liquida..o no filtro atual/)).toBeInTheDocument());
  });

  it("proxima pagina desabilitada quando nao ha cursor", async () => {
    vi.spyOn(apiClient, "getStatement").mockResolvedValue(PAGE_ALL);
    render(<SettlementsGrid />, { wrapper });
    const table = await screen.findByRole("table");
    await waitFor(() => within(table).getByText("US$ 17.094,67"));
    const proxima = screen.getByRole("button", { name: /pr.xima p.gina/i });
    expect(proxima).toBeDisabled();
  });
});
