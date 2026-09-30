import { useQuery } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { apiClient, type ApiClient } from "../api/client";
import {
  applyFilterChange,
  filtersToSearch,
  searchToFilters,
  type StatementFilters,
} from "../lib/urlFilters";

/**
 * Filtros e cursor do extrato vivem NA URL (sem store global — premissa D16): recarregar ou
 * compartilhar o link preserva a visão. Usa `replaceState` (não empilha histórico); o botão
 * Voltar sai do SPA, o que é aceitável sem router. Mudar um filtro reseta o cursor.
 */
export function useStatementFilters():
    [StatementFilters, (patch: StatementFilters) => void] {
  const [filters, setFilters] = useState<StatementFilters>(
      () => searchToFilters(window.location.search));

  const update = useCallback((patch: StatementFilters) => {
    setFilters((current) => {
      const next = applyFilterChange(current, patch);
      const search = filtersToSearch(next);
      window.history.replaceState(null, "",
          search ? `?${search}` : window.location.pathname);
      return next;
    });
  }, []);

  return [filters, update];
}

export function useSettlements(
    filters: StatementFilters, client: Pick<ApiClient, "getStatement"> = apiClient) {
  return useQuery({
    queryKey: ["settlements", filters],
    queryFn: () => client.getStatement(filtersToSearch(filters)),
  });
}
