/**
 * Idade da vigência em pt-BR. `now` injetável para teste puro (sem relógio real).
 * Vive em módulo próprio para o FxTicker exportar SÓ o componente (fast-refresh do Vite
 * reclama de um arquivo que mistura componente e função utilitária exportada).
 */
export function ageLabel(validFrom: string, now: number = Date.now()): string {
  const parsed = Date.parse(validFrom);
  if (Number.isNaN(parsed)) {
    return "—"; // timestamp malformado: melhor um traço honesto que "há NaN h" (B14)
  }
  const minutes = Math.max(0, Math.floor((now - parsed) / 60_000));
  if (minutes < 1) {
    return "agora";
  }
  if (minutes < 60) {
    return `há ${minutes} min`;
  }
  return `há ${Math.floor(minutes / 60)} h`;
}
