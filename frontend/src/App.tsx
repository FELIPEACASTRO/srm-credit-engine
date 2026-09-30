import { SimulationPanel } from "./components/SimulationPanel";
import { SettlementsGrid } from "./components/SettlementsGrid";
import { FxTicker } from "./components/FxTicker";

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
        <FxTicker />
      </header>
      <SimulationPanel />
      <SettlementsGrid />
    </main>
  );
}
