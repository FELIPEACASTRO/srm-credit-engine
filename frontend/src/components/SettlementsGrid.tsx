import { formatDateBr, formatMoney } from "../lib/money";
import { useSettlements, useStatementFilters } from "../hooks/useSettlements";

/**
 * O ledger da mesa (4.2.2): filtros e paginação SERVER-SIDE (keyset — não duplica nem
 * pula sob inserção concorrente), estado na URL, totais por moeda vindos do SQL.
 */
export function SettlementsGrid() {
  const [filters, update] = useStatementFilters();
  const { data, error, isFetching } = useSettlements(filters);

  return (
    <section className="card">
      <h2>Extrato de liquidações</h2>
      <form className="fields" onSubmit={(event) => event.preventDefault()}>
        <label>
          De
          <input type="date" value={filters.from ?? ""}
            onChange={(e) => update({ from: e.target.value })} />
        </label>
        <label>
          Até
          <input type="date" value={filters.to ?? ""}
            onChange={(e) => update({ to: e.target.value })} />
        </label>
        <label>
          Cedente
          <select value={filters.cedenteId ?? ""}
            onChange={(e) => update({ cedenteId: e.target.value })}>
            <option value="">Todos</option>
            <option value="1">1 · Alfa</option>
            <option value="2">2 · Beta</option>
            <option value="3">3 · Gama</option>
          </select>
        </label>
        <label>
          Moeda
          <select value={filters.paymentCurrency ?? ""}
            onChange={(e) => update({ paymentCurrency: e.target.value })}>
            <option value="">Todas</option>
            <option value="BRL">BRL</option>
            <option value="USD">USD</option>
          </select>
        </label>
      </form>

      {error && <p role="alert" className="error">Extrato indisponível — {error.message}</p>}

      <table>
        <caption className="sr-only">Liquidações registradas</caption>
        <thead>
          <tr>
            <th scope="col">Nº</th>
            <th scope="col">Data</th>
            <th scope="col">Cedente</th>
            <th scope="col">Tipo</th>
            <th scope="col">Moeda</th>
            <th scope="col" className="money">Valor de face</th>
            <th scope="col" className="money">Deságio</th>
            <th scope="col" className="money">Valor pago</th>
            <th scope="col">Operador</th>
          </tr>
        </thead>
        <tbody>
          {data?.items.map((s) => (
            <tr key={s.id}>
              <td className="mute">{s.id}</td>
              <td>{formatDateBr(s.pricingDate)}</td>
              <td className="mute">{s.cedenteId}</td>
              <td className="mute">{s.strategy}</td>
              <td>
                <span className={`badge ${s.paid.currency.toLowerCase()}`}>
                  {s.paid.currency}
                </span>
              </td>
              <td className="money">{formatMoney(s.faceValue, "BRL")}</td>
              <td className="money mute">{formatMoney(s.discount.amount, s.discount.currency)}</td>
              <td className="money">{formatMoney(s.paid.amount, s.paid.currency)}</td>
              <td className="mute">{s.settledBy}</td>
            </tr>
          ))}
          {data && data.items.length === 0 && (
            <tr>
              <td colSpan={9} className="empty">
                Nenhuma liquidação no filtro atual — simule um recebível acima e liquide.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      <div className="grid-footer">
        <p className="totals">
          {Object.entries(data?.totalsByCurrency ?? {}).map(([currency, total]) => (
            <span key={currency} className="total">
              Total {currency} <strong>{formatMoney(total, currency)}</strong>
            </span>
          ))}
        </p>
        <div className="actions" style={{ marginTop: 0 }}>
          {filters.cursor && (
            <button type="button" className="ghost" onClick={() => update({ cursor: "" })}>
              Primeira página
            </button>
          )}
          <button
            type="button"
            disabled={!data?.nextCursor || isFetching}
            onClick={() => data?.nextCursor && update({ cursor: data.nextCursor })}
          >
            Próxima página
          </button>
        </div>
      </div>
    </section>
  );
}
