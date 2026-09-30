import { useRef, useState } from "react";
import { ApiError, apiClient, type ApiClient, type Idempotent } from "../api/client";
import type { ReceivableResponse, SettlementResponse } from "../api/types";
import { formatMoney } from "../lib/money";
import { uuidv4 } from "../lib/uuid";

export interface SettleFormValues {
  cedenteId: string;
  type: string;
  faceValue: string;
  paymentCurrency: string;
  dueDate: string;
}

type SettleClient = Pick<ApiClient, "registerReceivable" | "settle">;

interface Props {
  form: SettleFormValues;
  expectedAmount: string | null;
  client?: SettleClient;
  operator?: string;
  onSettled?: (settlement: SettlementResponse) => void;
}

interface IntentionKeys {
  intention: string;
  creationKey: string;
  settleKey: string;
}

/**
 * "Cadastrar e liquidar" com Idempotency-Key POR INTENÇÃO (premissa B7): as chaves nascem
 * quando o formulário assume estes valores e são REUSADAS no retry — o replay do backend
 * garante que rede instável nunca duplica. Formulário diferente = intenção nova = chaves
 * novas. O duplo clique é bloqueado por ref síncrona (estado do React é assíncrono).
 */
export function SettleDialog({ form, expectedAmount, client = apiClient,
    operator = "mesa", onSettled }: Props) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<Idempotent<SettlementResponse> | null>(null);
  const busyRef = useRef(false);
  const keysRef = useRef<IntentionKeys | null>(null);

  const intention = JSON.stringify(form);
  if (keysRef.current?.intention !== intention) {
    keysRef.current = {
      intention,
      creationKey: uuidv4(),
      settleKey: uuidv4(),
    };
    if (done || error) {
      // intencao nova: resultado anterior nao se aplica mais
      setDone(null);
      setError(null);
    }
  }

  async function run() {
    if (busyRef.current) {
      return;
    }
    busyRef.current = true;
    setBusy(true);
    setError(null);
    try {
      const keys = keysRef.current!;
      const registered: Idempotent<ReceivableResponse> = await client.registerReceivable({
        cedenteId: Number(form.cedenteId),
        type: form.type,
        faceValue: form.faceValue,
        paymentCurrency: form.paymentCurrency,
        dueDate: form.dueDate,
      }, keys.creationKey);
      const settled = await client.settle(
          registered.body.id, keys.settleKey, expectedAmount, operator);
      setDone(settled);
      onSettled?.(settled.body);
    } catch (e) {
      if (e instanceof ApiError) {
        setError(`${e.code ?? e.status}: ${e.message}`);
      } else {
        setError((e as Error).message);
      }
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  if (done) {
    const s = done.body;
    return (
      <section className="card success" aria-live="polite">
        <h3>Liquidação nº {s.id} registrada{done.replayed ? " · replay idempotente" : ""}</h3>
        <p>
          Pago <strong>{formatMoney(s.paid.amount, s.paid.currency)}</strong> · deságio{" "}
          <strong>{formatMoney(s.discount.amount, s.discount.currency)}</strong> · câmbio{" "}
          <strong>{s.fx ? s.fx.rate : "—"}</strong> · operador <strong>{s.settledBy}</strong>
        </p>
      </section>
    );
  }

  return (
    <div className="actions">
      <button type="button" disabled={busy} onClick={run}>
        {busy ? "Liquidando…" : error ? "Tentar novamente" : "Cadastrar e liquidar"}
      </button>
      {error && (
        <p role="alert" className="error" style={{ margin: 0 }}>
          Falha ao liquidar — {error}
        </p>
      )}
    </div>
  );
}
