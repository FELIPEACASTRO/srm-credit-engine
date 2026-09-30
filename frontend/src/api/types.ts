/** Contratos da API. Dinheiro é SEMPRE string ("92859.94") — nunca number. */

export interface MoneyDto {
  amount: string;
  currency: string;
}

export interface FxSnapshot {
  rate: string;
  validFrom: string;
}

export interface FxDto extends FxSnapshot {
  id: number;
}

export interface SimulationResponse {
  termMonths: number;
  pricingDate: string;
  presentValue: MoneyDto;
  discount: MoneyDto;
  paid: MoneyDto;
  baseRate: string;
  spread: string;
  fx: FxSnapshot | null;
}

export interface SimulationRequest {
  type: string;
  faceValue: string;
  paymentCurrency: string;
  dueDate: string;
}

export interface RegisterReceivableRequest extends SimulationRequest {
  cedenteId: number;
}

export interface ReceivableResponse {
  id: number;
  cedenteId: number;
  type: string;
  faceValue: string;
  paymentCurrency: string;
  dueDate: string;
  status: "OPEN" | "SETTLED";
  version: number;
}

export interface SettlementResponse {
  id: number;
  receivableId: number;
  cedenteId: number;
  strategy: string;
  faceValue: string;
  termMonths: number;
  pricingDate: string;
  baseRate: string;
  spread: string;
  roundingMode: string;
  presentValue: MoneyDto;
  discount: MoneyDto;
  paid: MoneyDto;
  fx: FxDto | null;
  settledBy: string;
  settledAt: string;
}

export interface StatementPage {
  items: SettlementResponse[];
  nextCursor: string | null;
  totalsByCurrency: Record<string, string>;
}

export interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
  code?: string;
  errors?: Record<string, string>;
}
