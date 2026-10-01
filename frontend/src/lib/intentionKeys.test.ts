import { afterEach, describe, expect, it, vi } from "vitest";
import { keysFor } from "./intentionKeys";

describe("intentionKeys: par estavel por intencao, escopo de sessao", () => {
  afterEach(() => vi.restoreAllMocks());

  it("mesma intencao -> SEMPRE o mesmo par; intencao diferente -> par diferente", () => {
    const a1 = keysFor("int-A-estavel");
    const a2 = keysFor("int-A-estavel");
    const b = keysFor("int-B-outra");
    expect(a2.creationKey).toBe(a1.creationKey);
    expect(a2.settleKey).toBe(a1.settleKey);
    expect(b.creationKey).not.toBe(a1.creationKey);
    // os dois papeis nunca compartilham a mesma chave
    expect(a1.creationKey).not.toBe(a1.settleKey);
  });

  it("sem sessionStorage (contexto privado/negado): fallback em memoria preserva o par", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("denied");
    });
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("denied");
    });
    const first = keysFor("int-C-sem-storage");
    const again = keysFor("int-C-sem-storage");
    expect(again.creationKey).toBe(first.creationKey);
    expect(again.settleKey).toBe(first.settleKey);
  });

  it("valor corrompido no storage e ignorado e um par novo e gerado", () => {
    window.sessionStorage.setItem("srm.intention-keys:int-D-corrompida", "{nao-e-json");
    const keys = keysFor("int-D-corrompida");
    expect(typeof keys.creationKey).toBe("string");
    expect(keys.creationKey).toHaveLength(36);
  });
});
