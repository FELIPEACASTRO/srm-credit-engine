import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { formatMoney, parsePtBr } from "../lib/money";
import { useSimulation } from "../hooks/useSimulation";
import { SettleDialog } from "./SettleDialog";

/**
 * Painel do operador (4.2.1): input do recebível com simulação do valor líquido em tempo
 * real. O painel NÃO calcula nada — todo valor exibido veio do backend como string.
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
      <h2>Simulação e liquidação</h2>
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
            <option value="DUPLICATA">Duplicata Mercantil (1,5% a.m.)</option>
            <option value="CHEQUE">Cheque Pré-datado (2,5% a.m.)</option>
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
            <option value="1">1 — Alfa Distribuidora</option>
            <option value="2">2 — Beta Indústria</option>
            <option value="3">3 — Gama Comércio</option>
          </select>
        </label>
      </form>

      <div aria-live="polite" className="result">
        {loading && <p>Calculando…</p>}
        {error && (
          <p role="alert" className="error">Simulação indisponível — {error.message}</p>
        )}
        {data && !loading && (
          <>
            <p className="headline">
              Valor líquido:{" "}
              <strong>{formatMoney(data.paid.amount, data.paid.currency)}</strong>
            </p>
            <p>
              PV em BRL {formatMoney(data.presentValue.amount, "BRL")} · Deságio{" "}
              {formatMoney(data.discount.amount, "BRL")} · Prazo {data.termMonths}{" "}
              {data.termMonths === 1 ? "mês" : "meses"} · Taxa base {data.baseRate} + spread{" "}
              {data.spread}
              {data.fx && <> · Câmbio {data.fx.rate} (vigente desde {data.fx.validFrom})</>}
            </p>
          </>
        )}
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
