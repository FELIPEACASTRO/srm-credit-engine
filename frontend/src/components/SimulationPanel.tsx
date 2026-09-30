import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { formatMoney, parsePtBr } from "../lib/money";
import { useSimulation } from "../hooks/useSimulation";
import { SettleDialog } from "./SettleDialog";
import type { SimulationResponse } from "../api/types";

/**
 * Painel do operador (4.2.1). A assinatura da página: a EQUAÇÃO do deságio exposta —
 * cada termo que o backend usou vira um chip auditável. O painel não calcula nada;
 * todo número na tela veio do motor, como string.
 */
export function SimulationPanel() {
  const [rawValue, setRawValue] = useState("100.000,00");
  const [type, setType] = useState("DUPLICATA");
  const [currency, setCurrency] = useState("BRL");
  const [dueDate, setDueDate] = useState(() => {
    const inThreeMonths = new Date();
    inThreeMonths.setMonth(inThreeMonths.getMonth() + 3);
    return inThreeMonths.toISOString().slice(0, 10);
  });
  const [cedenteId, setCedenteId] = useState("1");

  const faceValue = parsePtBr(rawValue);
  const { data, error, loading } = useSimulation({
    type,
    faceValue,
    paymentCurrency: currency,
    dueDate,
  });
  const queryClient = useQueryClient();

  const invalidValue = rawValue.trim() !== "" && faceValue === null;

  return (
    <section className="card">
      <h2>Precificação</h2>
      <form className="fields" onSubmit={(event) => event.preventDefault()}>
        <label>
          Valor de face (R$)
          <input
            type="text"
            inputMode="decimal"
            value={rawValue}
            onChange={(event) => setRawValue(event.target.value)}
            aria-invalid={invalidValue}
          />
          {invalidValue && (
            <small className="error">Use o formato 100.000,00</small>
          )}
        </label>
        <label>
          Vencimento
          <input
            type="date"
            value={dueDate}
            onChange={(event) => setDueDate(event.target.value)}
          />
        </label>
        <label>
          Tipo
          <select value={type} onChange={(event) => setType(event.target.value)}>
            <option value="DUPLICATA">Duplicata Mercantil · 1,5% a.m.</option>
            <option value="CHEQUE">Cheque Pré-datado · 2,5% a.m.</option>
          </select>
        </label>
        <label>
          Moeda de pagamento
          <select value={currency} onChange={(event) => setCurrency(event.target.value)}>
            <option value="BRL">BRL</option>
            <option value="USD">USD</option>
          </select>
        </label>
        <label>
          Cedente
          <select value={cedenteId} onChange={(event) => setCedenteId(event.target.value)}>
            <option value="1">1 · Alfa Distribuidora</option>
            <option value="2">2 · Beta Indústria</option>
            <option value="3">3 · Gama Comércio</option>
          </select>
        </label>
      </form>

      <div aria-live="polite" className="result">
        {loading && <p className="loading">precificando</p>}
        {error && (
          <p role="alert" className="error">Simulação indisponível — {error.message}</p>
        )}
        {data && !loading && faceValue && <LiveEquation data={data} face={faceValue} />}
      </div>

      {data && faceValue && (
        <SettleDialog
          form={{ cedenteId, type, faceValue, paymentCurrency: currency, dueDate }}
          expectedAmount={data.paid.amount}
          onSettled={() => queryClient.invalidateQueries({ queryKey: ["settlements"] })}
        />
      )}
    </section>
  );
}

/** A equação viva: face → taxa ao mês → prazo → PV → deságio → (câmbio) → líquido. */
function LiveEquation({ data, face }: { data: SimulationResponse; face: string }) {
  return (
    <>
      <p className="headline-label">Valor líquido ao cedente</p>
      <p className="headline" key={data.paid.amount + data.paid.currency}>
        {formatMoney(data.paid.amount, data.paid.currency)}
      </p>
      <div className="equation" aria-label="Decomposição auditável do cálculo">
        <span className="eq-chip">
          <span className="k">Valor de face</span>
          <span className="v">{formatMoney(face, "BRL")}</span>
        </span>
        <span className="eq-op">÷</span>
        <span className="eq-chip">
          <span className="k">Taxa ao mês</span>
          <span className="v">{data.baseRate} + {data.spread}</span>
        </span>
        <span className="eq-op">^</span>
        <span className="eq-chip">
          <span className="k">Prazo</span>
          <span className="v">{data.termMonths} {data.termMonths === 1 ? "mês" : "meses"}</span>
        </span>
        <span className="eq-op">=</span>
        <span className="eq-chip">
          <span className="k">PV em BRL</span>
          <span className="v">{formatMoney(data.presentValue.amount, "BRL")}</span>
        </span>
        <span className="eq-chip">
          <span className="k">Deságio</span>
          <span className="v">{formatMoney(data.discount.amount, "BRL")}</span>
        </span>
        {data.fx && (
          <>
            <span className="eq-op">÷</span>
            <span className="eq-chip">
              <span className="k">Câmbio USD/BRL</span>
              <span className="v">{data.fx.rate}</span>
            </span>
          </>
        )}
        <span className="eq-op">=</span>
        <span className="eq-chip out">
          <span className="k">Líquido · half-even</span>
          <span className="v">{formatMoney(data.paid.amount, data.paid.currency)}</span>
        </span>
      </div>
    </>
  );
}
