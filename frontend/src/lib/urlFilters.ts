/** Filtros do extrato vivem na URL: compartilháveis, bookmarkáveis, sem store global. */
export interface StatementFilters {
  from?: string;
  to?: string;
  cedenteId?: string;
  paymentCurrency?: string;
  cursor?: string;
}

const KEYS = ["from", "to", "cedenteId", "paymentCurrency", "cursor"] as const;

export function filtersToSearch(filters: StatementFilters): string {
  const params = new URLSearchParams();
  for (const key of KEYS) {
    const value = filters[key];
    if (value) {
      params.set(key, value);
    }
  }
  return params.toString();
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export function searchToFilters(search: string): StatementFilters {
  const params = new URLSearchParams(search);
  const filters: StatementFilters = {};
  for (const key of KEYS) {
    const value = params.get(key);
    // Lixo colado na URL (?from=abc) seguiria verbatim ao backend (400) e deixaria o
    // input type=date vazio com o filtro "ativo": data malformada e descartada (B15).
    if (value && (key === "from" || key === "to") && !ISO_DATE.test(value)) {
      continue;
    }
    if (value) {
      filters[key] = value;
    }
  }
  return filters;
}

/** Mudou um FILTRO (não-cursor)? O cursor cai: página 1 do novo conjunto. */
export function applyFilterChange(
    current: StatementFilters, patch: StatementFilters): StatementFilters {
  const next: StatementFilters = { ...current, ...patch };
  if (Object.keys(patch).some((key) => key !== "cursor")) {
    delete next.cursor;
  }
  for (const key of KEYS) {
    if (!next[key]) {
      delete next[key];
    }
  }
  return next;
}
