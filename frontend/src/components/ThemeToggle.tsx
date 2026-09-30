import { useEffect, useState } from "react";

type Theme = "dark" | "light";

function initialTheme(): Theme {
  try {
    const saved = localStorage.getItem("srm-theme");
    if (saved === "light" || saved === "dark") {
      return saved;
    }
  } catch {
    // armazenamento indisponível (modo privado): segue o padrão
  }
  return "dark"; // o terminal escuro é a identidade padrão da mesa
}

/** Alterna terminal (escuro) ↔ ficha diurna (claro); a escolha persiste por operador. */
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(initialTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem("srm-theme", theme);
    } catch {
      // sem persistência: o tema ainda vale para a sessão
    }
  }, [theme]);

  const next: Theme = theme === "dark" ? "light" : "dark";
  return (
    <button
      type="button"
      className="ghost theme-toggle"
      onClick={() => setTheme(next)}
      aria-label={next === "light" ? "Mudar para o tema claro" : "Mudar para o tema escuro"}
      title={next === "light" ? "Tema claro" : "Tema escuro"}
    >
      {theme === "dark" ? "☀" : "☾"}
    </button>
  );
}
