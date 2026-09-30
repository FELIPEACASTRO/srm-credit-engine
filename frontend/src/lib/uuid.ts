/**
 * UUID v4 resiliente a contexto NAO-seguro. `crypto.randomUUID()` so existe em contexto
 * seguro (https ou localhost/127.0.0.1); acessando a demo por http://<IP-da-LAN> ele e
 * `undefined` e um `crypto.randomUUID()` no render derrubaria a tela. Aqui: usa o nativo
 * quando disponivel, senao monta o v4 a partir de `crypto.getRandomValues` (disponivel em
 * praticamente todo contexto), e so em ultimo caso cai para Math.random (chave de
 * idempotencia nao e segredo — precisa ser unica, nao cripto-forte).
 */
export function uuidv4(): string {
  const c = globalThis.crypto as Crypto | undefined;
  if (c && typeof c.randomUUID === "function") {
    return c.randomUUID();
  }
  const bytes = new Uint8Array(16);
  if (c && typeof c.getRandomValues === "function") {
    c.getRandomValues(bytes);
  } else {
    for (let i = 0; i < 16; i++) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  bytes[6] = (bytes[6]! & 0x0f) | 0x40; // versao 4
  bytes[8] = (bytes[8]! & 0x3f) | 0x80; // variante
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0"));
  return (
    hex.slice(0, 4).join("") +
    "-" +
    hex.slice(4, 6).join("") +
    "-" +
    hex.slice(6, 8).join("") +
    "-" +
    hex.slice(8, 10).join("") +
    "-" +
    hex.slice(10, 16).join("")
  );
}
