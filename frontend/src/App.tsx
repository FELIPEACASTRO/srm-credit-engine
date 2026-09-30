import { SimulationPanel } from "./components/SimulationPanel";
import { SettlementsGrid } from "./components/SettlementsGrid";
import { FxTicker } from "./components/FxTicker";
import { ThemeToggle } from "./components/ThemeToggle";

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
          <FxTicker />
          <ThemeToggle />
        </div>
      </header>
      <SimulationPanel />
      <SettlementsGrid />
    </main>
  );
}
