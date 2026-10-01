import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
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
  const client = {
    registerReceivable: vi.fn(async (_body: unknown, _key: string) =>
        ({ body: receivable, replayed: false })),
    settle: vi.fn(async (_id: number, _key: string) =>
        ({ body: settlement, replayed: false })),
  };
  // as chaves saem de mock.calls: capturam TODA chamada, inclusive as rejeitadas
  // (mockRejectedValueOnce substitui a implementacao e pularia um push manual)
  const registerKeys = () => client.registerReceivable.mock.calls.map((c) => c[1]);
  const settleKeys = () => client.settle.mock.calls.map((c) => c[1]);
  return { client, registerKeys, settleKeys };
}

describe("SettleDialog: chave por intencao, anti-duplo-clique", () => {
  afterEach(() => cleanup());

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

    await waitFor(() => expect(settleKeys().length).toBe(2));
    expect(settleKeys()[0]).toBe(settleKeys()[1]);
    expect(registerKeys()[0]).toBe(registerKeys()[1]);
  });

  it("mudar o formulario gera chaves NOVAS (outra intencao)", async () => {
    const { client, settleKeys } = clients();
    const { rerender } = render(
      <SettleDialog form={form} expectedAmount="92859.94" client={client} />,
    );
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: /cadastrar e liquidar/i }));
    await waitFor(() => expect(settleKeys().length).toBe(1));

    rerender(<SettleDialog form={{ ...form, faceValue: "50000.00" }}
        expectedAmount="46429.97" client={client} />);
    await user.click(screen.getByRole("button", { name: /cadastrar e liquidar/i }));
    await waitFor(() => expect(settleKeys().length).toBe(2));
    expect(settleKeys()[1]).not.toBe(settleKeys()[0]);
  });

  it("desvio A->B->A e REMONTAGEM preservam as chaves da intencao (retry replaya, nao duplica cadastro)", async () => {
    const { client, registerKeys, settleKeys } = clients();
    // o PIOR caso do A1: cadastro SUCEDE e a liquidacao falha — chave de criacao nova
    // no retry cadastraria um SEGUNDO recebivel (o replay nao dispara) e o original
    // ficaria OPEN orfao
    client.settle.mockRejectedValueOnce(new Error("rede caiu"));
    const formA = { ...form, faceValue: "77777.00" }; // intencao exclusiva deste teste

    const first = render(
        <SettleDialog form={formA} expectedAmount="72222.11" client={client} />);
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: /cadastrar e liquidar/i }));
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());

    // operador desvia a intencao para B e volta para A...
    first.rerender(<SettleDialog form={{ ...formA, faceValue: "88888.00" }}
        expectedAmount="82539.68" client={client} />);
    first.rerender(<SettleDialog form={formA} expectedAmount="72222.11" client={client} />);
    // ...e o painel REMONTA o dialogo (a re-simulacao desmonta durante o loading)
    first.unmount();
    render(<SettleDialog form={formA} expectedAmount="72222.11" client={client} />);

    await user.click(screen.getByRole("button", { name: /cadastrar e liquidar/i }));
    await waitFor(() => expect(settleKeys().length).toBe(2));
    expect(registerKeys()[1]).toBe(registerKeys()[0]); // MESMA creationKey => replay
    expect(settleKeys()[1]).toBe(settleKeys()[0]);     // MESMA settleKey   => replay
  });

  it("apos sucesso, trocar o form ESCONDE o card da intencao antiga (sem flash de dados velhos)", async () => {
    const { client } = clients();
    const { rerender } = render(
        <SettleDialog form={form} expectedAmount="92859.94" client={client} />);
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: /cadastrar e liquidar/i }));
    // card de sucesso da intencao A aparece
    await waitFor(() => expect(screen.getByText(/Liquida..o n. 10 registrada/)).toBeInTheDocument());

    // troca o form (intencao B): o card da intencao A NAO pode continuar na tela
    rerender(<SettleDialog form={{ ...form, faceValue: "25000.00" }}
        expectedAmount="23337.77" client={client} />);
    expect(screen.queryByText(/Liquida..o n. 10 registrada/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /cadastrar e liquidar/i })).toBeInTheDocument();
  });
});
