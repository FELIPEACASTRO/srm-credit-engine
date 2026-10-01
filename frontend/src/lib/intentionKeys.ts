import { uuidv4 } from "./uuid";

export interface IntentionKeys {
  creationKey: string;
  settleKey: string;
}

const PREFIX = "srm.intention-keys:";
// Fallback em memória para contexto sem sessionStorage (modo privado, storage negado).
const memory = new Map<string, IntentionKeys>();

function read(intention: string): IntentionKeys | null {
  try {
    const raw = window.sessionStorage.getItem(PREFIX + intention);
    if (raw) {
      const parsed = JSON.parse(raw) as IntentionKeys;
      if (typeof parsed.creationKey === "string" && typeof parsed.settleKey === "string") {
        return parsed;
      }
    }
  } catch {
    // storage inacessível ou JSON corrompido: cai para a memória do módulo
  }
  return memory.get(intention) ?? null;
}

function write(intention: string, keys: IntentionKeys): void {
  memory.set(intention, keys);
  try {
    window.sessionStorage.setItem(PREFIX + intention, JSON.stringify(keys));
  } catch {
    // quota/contexto privado: a memória do módulo ainda cobre esta aba
  }
}

/**
 * Chaves de idempotência POR INTENÇÃO com escopo de SESSÃO (aba): a mesma intenção
 * devolve SEMPRE o mesmo par — inclusive depois de desviar o formulário e voltar
 * (A→B→A) e depois de o componente REMONTAR (a re-simulação desmonta o diálogo).
 * Sem isso, "cadastro ok + liquidação falhou + desvio e volta + tentar novamente"
 * cadastraria um SEGUNDO recebível (chave nova não dispara o replay do backend),
 * deixando o original OPEN órfão — achado A1 do code review.
 *
 * Deliberado NÃO derivar a chave só do conteúdo (ex.: UUIDv5 do payload): uma
 * duplicata idêntica LEGÍTIMA registrada noutra sessão deve ser outra operação.
 * O escopo certo de "mesma intenção" é a sessão da aba — exatamente o que o
 * sessionStorage delimita.
 */
export function keysFor(intention: string): IntentionKeys {
  const existing = read(intention);
  if (existing) {
    return existing;
  }
  const fresh: IntentionKeys = { creationKey: uuidv4(), settleKey: uuidv4() };
  write(intention, fresh);
  return fresh;
}
