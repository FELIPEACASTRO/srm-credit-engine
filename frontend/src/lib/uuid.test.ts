import { afterEach, describe, expect, it, vi } from "vitest";
import { uuidv4 } from "./uuid";

const V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe("uuidv4: resiliente a contexto nao-seguro", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("usa o nativo quando disponivel", () => {
    expect(uuidv4()).toMatch(V4);
  });

  it("cai para getRandomValues quando randomUUID nao existe (http por IP da LAN)", () => {
    vi.stubGlobal("crypto", {
      getRandomValues: (a: Uint8Array) => {
        for (let i = 0; i < a.length; i++) a[i] = i;
        return a;
      },
    });
    const id = uuidv4();
    expect(id).toMatch(V4); // gera v4 valido, sem lancar
  });

  it("cai para Math.random se nao houver crypto algum, e ainda gera v4 valido e unico", () => {
    vi.stubGlobal("crypto", undefined);
    const a = uuidv4();
    const b = uuidv4();
    expect(a).toMatch(V4);
    expect(b).toMatch(V4);
    expect(a).not.toBe(b);
  });
});
