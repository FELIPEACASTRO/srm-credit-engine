import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { SimulationResponse } from "../api/types";
import type { DecimalString } from "../lib/money";
import { useSimulation, type SimulationInput } from "./useSimulation";

function response(pv: string): SimulationResponse {
  return {
    termMonths: 3,
    pricingDate: "2026-09-30",
    presentValue: { amount: pv, currency: "BRL" },
    discount: { amount: "1.00", currency: "BRL" },
    paid: { amount: pv, currency: "BRL" },
    baseRate: "0.010000",
    spread: "0.015",
    fx: null,
  };
}

const input = (faceValue: string): SimulationInput => ({
  type: "DUPLICATA",
  faceValue: faceValue as DecimalString,
  paymentCurrency: "BRL",
  dueDate: "2026-12-30",
});

describe("useSimulation: debounce + guarda de sequencia", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("respostas fora de ordem: a tela mostra a ULTIMA entrada, nunca a resposta atrasada", async () => {
    const resolvers: Array<(r: SimulationResponse) => void> = [];
    const simulate = vi.fn(
      () => new Promise<SimulationResponse>((resolve) => resolvers.push(resolve)),
    );

    const { result, rerender } = renderHook(
      ({ i }) => useSimulation(i, { simulate }),
      { initialProps: { i: input("1000.00") } },
    );

    await act(async () => {
      vi.advanceTimersByTime(300);
    });
    rerender({ i: input("2000.00") });
    await act(async () => {
      vi.advanceTimersByTime(300);
    });
    expect(simulate).toHaveBeenCalledTimes(2);

    // a resposta NOVA chega primeiro; a VELHA chega atrasada e deve ser descartada
    await act(async () => {
      resolvers[1]!(response("1975.00"));
    });
    expect(result.current.data?.presentValue.amount).toBe("1975.00");

    await act(async () => {
      resolvers[0]!(response("987.00"));
    });
    expect(result.current.data?.presentValue.amount).toBe("1975.00");
  });

  it("input invalido nao chama a API e limpa o resultado", async () => {
    const simulate = vi.fn();
    const { result, rerender } = renderHook(
      ({ i }) => useSimulation(i, { simulate }),
      { initialProps: { i: input("1000.00") } },
    );
    rerender({ i: { ...input("1000.00"), faceValue: null } });
    await act(async () => {
      vi.advanceTimersByTime(400);
    });
    expect(result.current.data).toBeNull();
  });

  it("digitacao rapida dispara UMA chamada (debounce)", async () => {
    const simulate = vi.fn(async () => response("1.00"));
    const { rerender } = renderHook(
      ({ i }) => useSimulation(i, { simulate }),
      { initialProps: { i: input("1.00") } },
    );
    rerender({ i: input("12.00") });
    rerender({ i: input("123.00") });
    await act(async () => {
      vi.advanceTimersByTime(300);
    });
    expect(simulate).toHaveBeenCalledTimes(1);
  });
});
