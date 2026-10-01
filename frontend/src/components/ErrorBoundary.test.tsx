import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ErrorBoundary } from "./ErrorBoundary";

function Bomb(): never {
  throw new Error("escala invalida para dinheiro: '92859.9'");
}

describe("ErrorBoundary por secao: contem a falha sem derrubar o resto", () => {
  afterEach(() => cleanup());

  it("a secao quebrada vira card de alerta e o IRMAO continua renderizado", () => {
    // formatMoney lanca por contrato; sem boundary POR secao, um unico valor com
    // escala errada no extrato derrubaria a tela inteira da mesa (achado M1)
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    render(
      <div>
        <ErrorBoundary label="extrato">
          <Bomb />
        </ErrorBoundary>
        <p>simulação viva</p>
      </div>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(/extrato/);
    expect(screen.getByText(/escala invalida/)).toBeInTheDocument();
    expect(screen.getByText("simulação viva")).toBeInTheDocument();
    spy.mockRestore();
  });

  it("sem falha, renderiza os filhos sem interferir", () => {
    render(
      <ErrorBoundary label="simulação">
        <p>conteúdo normal</p>
      </ErrorBoundary>,
    );
    expect(screen.getByText("conteúdo normal")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
