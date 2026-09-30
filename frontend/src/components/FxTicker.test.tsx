import { describe, expect, it } from "vitest";
import { ageLabel } from "./FxTicker";

const base = Date.parse("2026-09-30T12:00:00Z");

describe("ageLabel: idade da vigencia da cotacao (now injetavel)", () => {
  it("agora, minutos e horas", () => {
    expect(ageLabel("2026-09-30T12:00:00Z", base + 30_000)).toBe("agora");
    expect(ageLabel("2026-09-30T12:00:00Z", base + 5 * 60_000)).toBe("há 5 min");
    expect(ageLabel("2026-09-30T12:00:00Z", base + 3 * 3_600_000)).toBe("há 3 h");
  });

  it("avanca com o tempo (nao congela): o mesmo validFrom lido em instantes diferentes muda", () => {
    const vf = "2026-09-30T12:00:00Z";
    expect(ageLabel(vf, base + 60_000)).toBe("há 1 min");
    expect(ageLabel(vf, base + 120_000)).toBe("há 2 min");
  });

  it("nunca negativo se o validFrom for futuro (relogios dessincronizados)", () => {
    expect(ageLabel("2026-09-30T12:10:00Z", base)).toBe("agora");
  });
});
