import { useRef, useState } from "react";
import { ApiError, apiClient, type ApiClient, type Idempotent } from "../api/client";
import type { ReceivableResponse, SettlementResponse } from "../api/types";
import { keysFor } from "../lib/intentionKeys";
import { formatMoney } from "../lib/money";

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

/**
 * "Cadastrar e liquidar" com Idempotency-Key POR INTENÇÃO (premissa B7): as chaves são
 * um PAR ESTÁVEL por conteúdo do formulário com escopo de SESSÃO (lib/intentionKeys) —
 * sobrevivem a desvio-e-volta (A→B→A) e à REMONTAGEM do diálogo na re-simulação, então
 * o retry depois de falha parcial dispara o replay do backend em vez de cadastrar um
 * segundo recebível (achado A1 do code review). Formulário diferente = intenção nova =
 * chaves novas. O duplo clique é bloqueado por ref síncrona (estado do React é assíncrono).
 * Nota de contrato: expectedAmount NÃO entra na intenção de propósito — uma mera
 * reprecificação (mesmo form, preço novo) deve reusar o MESMO cadastro, não criar outro.
 */
export function SettleDialog({ form, expectedAmount, client = apiClient,
    operator = "mesa", onSettled }: Props) {
  const [busy, setBusy] = useState(false);
  // resultado/erro carregam a intencao que os produziu; assim a UI nunca mostra o card de
  // sucesso (ou o erro) de uma intencao anterior sob um formulario ja alterado — sem
  // recorrer a setState durante o render (anti-padrao que causava o "flash" de dados velhos).
  const [done, setDone] = useState<{ result: Idempotent<SettlementResponse>; intention: string } | null>(null);
  const [error, setError] = useState<{ message: string; intention: string } | null>(null);
  const busyRef = useRef(false);

  const intention = JSON.stringify(form);
  const keys = keysFor(intention);
  const currentDone = done?.intention === intention ? done.result : null;
  const currentError = error?.intention === intention ? error.message : null;

  async function run() {
    if (busyRef.current) {
      return;
    }
    const forIntention = intention;
    busyRef.current = true;
    setBusy(true);
    setError(null);
    try {
      const registered: Idempotent<ReceivableResponse> = await client.registerReceivable({
        cedenteId: Number(form.cedenteId),
        type: form.type,
        faceValue: form.faceValue,
        paymentCurrency: form.paymentCurrency,
        dueDate: form.dueDate,
      }, keys.creationKey);
      const settled = await client.settle(
          registered.body.id, keys.settleKey, expectedAmount, operator);
      setDone({ result: settled, intention: forIntention });
      onSettled?.(settled.body);
    } catch (e) {
      const message = e instanceof ApiError
          ? `${e.code ?? e.status}: ${e.message}`
          : (e as Error).message;
      setError({ message, intention: forIntention });
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  if (currentDone) {
    const s = currentDone.body;
    return (
      <section className="card success" aria-live="polite">
        <h3>Liquidação nº {s.id} registrada{currentDone.replayed ? " · replay idempotente" : ""}</h3>
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
        {busy ? "Liquidando…" : currentError ? "Tentar novamente" : "Cadastrar e liquidar"}
      </button>
      {currentError && (
        <p role="alert" className="error" style={{ margin: 0 }}>
          Falha ao liquidar — {currentError}
        </p>
      )}
    </div>
  );
}
