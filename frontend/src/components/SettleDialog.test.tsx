import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { ReceivableResponse, SettlementResponse } from "../api/types";
import { SettleDialog } from "./SettleDialog";

const receivable: ReceivableResponse = {
  id: 1, cedenteId: 1, type: "DUPLICATA", faceValue: "100000.00",
  paymentCurrency: "BRL", dueDate: "2026-12-30", status: "OPEN", version: 0,
};

const settlement = {
  id: 10, receivableId: 1, cedenteId: 1, strategy: "DUPLICATA", faceValue: "100000.00",
  termMonths: 3, pricingDate: "2026-09-30", baseRate: "0.010000", spread: "0.015000",
  roundingMode: "HALF_EVEN",
  presentValue: { amount: "92859.94", currency: "BRL" },
  discount: { amount: "7140.06", currency: "BRL" },
  paid: { amount: "92859.94", currency: "BRL" },
  fx: null, settledBy: "mesa", settledAt: "2026-09-30T12:00:00Z",
} satisfies SettlementResponse;

const form = {
  cedenteId: "1", type: "DUPLICATA", faceValue: "100000.00" as const,
  paymentCurrency: "BRL", dueDate: "2026-12-30",
};

function clients() {
  const registerKeys: string[] = [];
  const settleKeys: string[] = [];
  return {
    registerKeys,
    settleKeys,
    client: {
      registerReceivable: vi.fn(async (_body: unknown, key: string) => {
        registerKeys.push(key);
        return { body: receivable, replayed: false };
      }),
      settle: vi.fn(async (_id: number, key: string) => {
        settleKeys.push(key);
        return { body: settlement, replayed: false };
      }),
    },
  };
}

describe("SettleDialog: chave por intencao, anti-duplo-clique", () => {
  it("duplo clique = 1 cadastro + 1 liquidacao (botao trava durante o voo)", async () => {
    const { client } = clients();
    render(<SettleDialog form={form} expectedAmount="92859.94" client={client} />);

    const button = screen.getByRole("button", { name: /cadastrar e liquidar/i });
    const user = userEvent.setup();
    await user.dblClick(button);

    await waitFor(() => expect(client.settle).toHaveBeenCalledTimes(1));
    expect(client.registerReceivable).toHaveBeenCalledTimes(1);
  });

  it("retry apos erro REUSA as mesmas chaves (o replay do backend faz o resto)", async () => {
    const { client, registerKeys, settleKeys } = clients();
    client.settle.mockRejectedValueOnce(new Error("rede caiu"));

    render(<SettleDialog form={form} expectedAmount="92859.94" client={client} />);
    const button = screen.getByRole("button", { name: /cadastrar e liquidar/i });
    const user = userEvent.setup();

    await user.click(button);
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /tentar novamente/i }));

    await waitFor(() => expect(settleKeys.length).toBe(2));
    expect(settleKeys[0]).toBe(settleKeys[1]);
    expect(registerKeys[0]).toBe(registerKeys[1]);
  });

  it("mudar o formulario gera chaves NOVAS (outra intencao)", async () => {
    const { client, settleKeys } = clients();
    const { rerender } = render(
      <SettleDialog form={form} expectedAmount="92859.94" client={client} />,
    );
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: /cadastrar e liquidar/i }));
    await waitFor(() => expect(settleKeys.length).toBe(1));

    rerender(<SettleDialog form={{ ...form, faceValue: "50000.00" }}
        expectedAmount="46429.97" client={client} />);
    await user.click(screen.getByRole("button", { name: /cadastrar e liquidar/i }));
    await waitFor(() => expect(settleKeys.length).toBe(2));
    expect(settleKeys[1]).not.toBe(settleKeys[0]);
  });
});
