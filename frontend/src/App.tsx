import { SimulationPanel } from "./components/SimulationPanel";
import { SettlementsGrid } from "./components/SettlementsGrid";

export function App() {
  return (
    <main>
      <header>
        <h1>SRM Credit Engine</h1>
        <p>Mesa de operações — precificação e liquidação de recebíveis (BRL/USD)</p>
      </header>
      <SimulationPanel />
      <SettlementsGrid />
    </main>
  );
}
