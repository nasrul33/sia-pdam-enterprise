export const THEME_STORAGE_KEY = "sia-pdam-theme";

export type Theme = "light" | "dark";

export function parseStoredTheme(value: unknown): Theme | null {
  return value === "light" || value === "dark" ? value : null;
}

export function resolveTheme(storedTheme: unknown, systemPrefersDark: boolean): Theme {
  return parseStoredTheme(storedTheme) ?? (systemPrefersDark ? "dark" : "light");
}
