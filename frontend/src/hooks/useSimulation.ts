import { useEffect, useRef, useState } from "react";
import type { ApiClient } from "../api/client";
import { ApiError, apiClient } from "../api/client";
import type { SimulationResponse } from "../api/types";
import type { DecimalString } from "../lib/money";

export interface SimulationInput {
  type: string;
  faceValue: DecimalString | null;
  paymentCurrency: string;
  dueDate: string;
}

interface SimulationState {
  data: SimulationResponse | null;
  error: ApiError | Error | null;
  loading: boolean;
}

/**
 * Simulação "em tempo real" (4.2.1): debounce + GUARDA DE SEQUÊNCIA — uma resposta
 * atrasada de um input antigo nunca sobrescreve a do input atual. O cálculo é sempre
 * do backend (fonte única de verdade); aqui não existe fórmula.
 */
export function useSimulation(
    input: SimulationInput,
    client: Pick<ApiClient, "simulate"> = apiClient,
    debounceMs = 300,
): SimulationState {
  const [state, setState] = useState<SimulationState>(
      { data: null, error: null, loading: false });
  const sequence = useRef(0);
  // O cliente fica em ref (não em deps): um chamador que recrie o objeto por render
  // não pode disparar o efeito em loop — o gatilho é SEMPRE a mudança do input.
  const clientRef = useRef(client);
  clientRef.current = client;

  const inputKey =
      `${input.type}|${input.faceValue ?? ""}|${input.paymentCurrency}|${input.dueDate}`;

  useEffect(() => {
    if (!input.faceValue || !input.dueDate) {
      sequence.current++;
      setState({ data: null, error: null, loading: false });
      return;
    }
    const mySequence = ++sequence.current;
    setState((previous) => ({ ...previous, loading: true }));
    const timer = setTimeout(() => {
      clientRef.current.simulate({
        type: input.type,
        faceValue: input.faceValue as string,
        paymentCurrency: input.paymentCurrency,
        dueDate: input.dueDate,
      }).then(
          (data) => {
            if (sequence.current === mySequence) {
              setState({ data, error: null, loading: false });
            }
          },
          (error: Error) => {
            if (sequence.current === mySequence) {
              setState({ data: null, error, loading: false });
            }
          },
      );
    }, debounceMs);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inputKey, debounceMs]);

  return state;
}
