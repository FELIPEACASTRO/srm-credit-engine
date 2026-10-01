import { Component, type ErrorInfo, type ReactNode } from "react";

interface Props {
  children: ReactNode;
  /** Nome da seção protegida (ex.: "extrato"); sem label, é a rede da tela inteira. */
  label?: string;
}

interface State {
  error: Error | null;
}

/**
 * Rede de segurança da UI em DUAS camadas: uma boundary POR SEÇÃO (simulação, extrato,
 * ticker) + uma raiz de última instância. Com `formatMoney` lançando por contrato
 * (escala errada nunca é exibida), uma única linha ruim do extrato derrubaria a tela
 * INTEIRA da mesa se a boundary fosse só a raiz (achado M1 do code review) — por seção,
 * a falha fica contida e o resto da mesa segue operando.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // eslint-disable-next-line no-console
    console.error(
      `Falha de renderizacao${this.props.label ? ` na secao "${this.props.label}"` : ""}:`,
      error, info.componentStack);
  }

  render(): ReactNode {
    if (this.state.error) {
      const where = this.props.label ? `na seção “${this.props.label}”` : "nesta tela";
      return (
        <section className="card" role="alert">
          <h2>Algo quebrou {where}</h2>
          <p>
            Nenhuma liquidação foi afetada — a falha é só de exibição
            {this.props.label ? "; o resto da mesa segue funcionando" : ""}. Recarregue a
            página para tentar de novo e, se persistir, avise o time com a mensagem abaixo.
          </p>
          <pre style={{ whiteSpace: "pre-wrap", color: "var(--red)" }}>
            {this.state.error.message}
          </pre>
          <button type="button" onClick={() => window.location.reload()}>
            Recarregar
          </button>
        </section>
      );
    }
    return this.props.children;
  }
}
