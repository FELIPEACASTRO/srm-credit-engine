import type {
  ExchangeRateDto,
  ProblemDetail,
  ReceivableResponse,
  RegisterReceivableRequest,
  SettlementResponse,
  SimulationRequest,
  SimulationResponse,
  StatementPage,
} from "./types";

/** Erro estruturado vindo do problem+json do backend: o código estável guia a UI. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string | undefined;
  readonly errors: Record<string, string> | undefined;

  constructor(status: number, code: string | undefined, detail: string | undefined,
      errors?: Record<string, string>) {
    super(detail ?? `HTTP ${status}`);
    this.status = status;
    this.code = code;
    this.errors = errors;
  }
}

/** replayed=true: o backend devolveu a operação ORIGINAL (retry idempotente). */
export interface Idempotent<T> {
  body: T;
  replayed: boolean;
}

const BASE = "/api/v1";

async function request<T>(path: string, init?: RequestInit): Promise<Idempotent<T>> {
  const response = await fetch(BASE + path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!response.ok) {
    let problem: ProblemDetail = {};
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // corpo não-JSON: segue com o status
    }
    throw new ApiError(response.status, problem.code, problem.detail, problem.errors);
  }
  let parsed: unknown;
  try {
    parsed = await response.json();
  } catch {
    // 2xx com corpo invalido: "Unexpected token..." e jargao de parser, nao mensagem de
    // mesa — vira ApiError acionavel em vez de SyntaxError cru na UI (B11)
    throw new ApiError(response.status, "invalid-response",
        "Resposta invalida do servidor; tente novamente");
  }
  return {
    body: parsed as T,
    replayed: response.headers.get("Idempotent-Replayed") === "true",
  };
}

export const apiClient = {
  async simulate(body: SimulationRequest): Promise<SimulationResponse> {
    const { body: result } = await request<SimulationResponse>("/simulations", {
      method: "POST",
      body: JSON.stringify(body),
    });
    return result;
  },

  registerReceivable(
      body: RegisterReceivableRequest, idempotencyKey: string,
  ): Promise<Idempotent<ReceivableResponse>> {
    return request<ReceivableResponse>("/receivables", {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(body),
    });
  },

  settle(
      receivableId: number, idempotencyKey: string, expectedAmount: string | null,
      operator: string,
  ): Promise<Idempotent<SettlementResponse>> {
    return request<SettlementResponse>(`/receivables/${receivableId}/settlement`, {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey, "X-Operator": operator },
      body: JSON.stringify(expectedAmount === null ? {} : { expectedAmount }),
    });
  },

  async getStatement(search: string): Promise<StatementPage> {
    const { body } = await request<StatementPage>(
        `/settlements${search ? "?" + search : ""}`, { method: "GET" });
    return body;
  },

  async getCurrentRate(base: string, quote: string): Promise<ExchangeRateDto> {
    const { body } = await request<ExchangeRateDto>(
        `/exchange-rates/current?base=${base}&quote=${quote}`, { method: "GET" });
    return body;
  },
};

export type ApiClient = typeof apiClient;
