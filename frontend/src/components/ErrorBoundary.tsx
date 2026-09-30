import { Component, type ErrorInfo, type ReactNode } from "react";

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Rede de seguranca da UI: uma excecao no render de qualquer tela vira uma mensagem
 * acionavel, nunca a tela branca. Sem isto, um unico throw em render (ex.: uma API do
 * browser ausente) derrubaria a mesa inteira sem pista.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // eslint-disable-next-line no-console
    console.error("Falha de renderizacao na UI da mesa:", error, info.componentStack);
  }

  render(): ReactNode {
    if (this.state.error) {
      return (
        <main>
          <section className="card" role="alert">
            <h2>Algo quebrou nesta tela</h2>
            <p>
              A operacao nao foi afetada — nenhuma liquidacao depende desta tela para
              existir. Recarregue a pagina para continuar. Se persistir, avise o time com a
              mensagem abaixo.
            </p>
            <pre style={{ whiteSpace: "pre-wrap", color: "var(--red)" }}>
              {this.state.error.message}
            </pre>
            <button type="button" onClick={() => window.location.reload()}>
              Recarregar
            </button>
          </section>
        </main>
      );
    }
    return this.props.children;
  }
}
