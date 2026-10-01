import { SimulationPanel } from "./components/SimulationPanel";
import { SettlementsGrid } from "./components/SettlementsGrid";
import { FxTicker } from "./components/FxTicker";
import { ThemeToggle } from "./components/ThemeToggle";
import { ErrorBoundary } from "./components/ErrorBoundary";

export function App() {
  return (
    <main>
      <header className="topbar">
        <div className="brand">
          <h1>
            SRM <span className="accent">Credit Engine</span>
          </h1>
          <span className="env">mesa · BRL/USD</span>
        </div>
        <div className="topbar-right">
          <ErrorBoundary label="ticker de câmbio">
            <FxTicker />
          </ErrorBoundary>
          <ThemeToggle />
        </div>
      </header>
      {/* Boundary POR seção: uma linha ruim do extrato não pode derrubar a simulação
          (formatMoney lança por contrato — a contenção tem que ser local). */}
      <ErrorBoundary label="simulação">
        <SimulationPanel />
      </ErrorBoundary>
      <ErrorBoundary label="extrato">
        <SettlementsGrid />
      </ErrorBoundary>
    </main>
  );
}
