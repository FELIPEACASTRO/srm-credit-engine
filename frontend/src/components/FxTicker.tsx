import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../api/client";
import { ageLabel } from "../lib/age";

/** Cotação vigente sempre à vista — com a IDADE da vigência, porque taxa sem idade não se audita. */
export function FxTicker() {
  const { data, isError } = useQuery({
    queryKey: ["fx", "USD", "BRL"],
    queryFn: () => apiClient.getCurrentRate("USD", "BRL"),
    refetchInterval: 60_000,
    retry: 1,
  });

  // Tique proprio de 30s: a idade avanca mesmo entre refetches, sem depender do React Query
  // re-renderizar quando a cotacao nao muda.
  const [, tick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => tick((n) => n + 1), 30_000);
    return () => clearInterval(id);
  }, []);

  // So "sem cotacao" quando NUNCA houve uma: um blip de rede num refetch de fundo
  // (isError com data presente) nao pode sumir com uma taxa possivelmente ainda
  // vigente — ela segue exibida, marcada como desatualizada (B14).
  if (isError && !data) {
    return (
      <div className="ticker stale" title="Sem cotação vigente utilizável">
        <span className="dot" />
        <span className="pair">USD/BRL</span>
        <span className="age">sem cotação — registre uma nova</span>
      </div>
    );
  }
  if (!data) {
    return (
      <div className="ticker">
        <span className="pair">USD/BRL</span>
        <span className="age">carregando…</span>
      </div>
    );
  }
  return (
    <div className={isError ? "ticker stale" : "ticker"}
        title={isError
            ? `Atualização falhou; exibindo a última vigente (desde ${data.validFrom})`
            : `Vigente desde ${data.validFrom}`}>
      <span className="dot" />
      <span className="pair">USD/BRL</span>
      <span className="rate">{data.rate}</span>
      <span className="age">{ageLabel(data.validFrom)}</span>
    </div>
  );
}
