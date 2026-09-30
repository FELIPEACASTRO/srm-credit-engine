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

export function searchToFilters(search: string): StatementFilters {
  const params = new URLSearchParams(search);
  const filters: StatementFilters = {};
  for (const key of KEYS) {
    const value = params.get(key);
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
