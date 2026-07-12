"use client";

import { Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";

import { parseStoredTheme, resolveTheme, THEME_STORAGE_KEY, type Theme } from "@/features/theme/theme";

const DARK_MEDIA_QUERY = "(prefers-color-scheme: dark)";

function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
}

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>("light");

  useEffect(() => {
    const mediaQuery = window.matchMedia(DARK_MEDIA_QUERY);

    const syncTheme = () => {
      const nextTheme = resolveTheme(window.localStorage.getItem(THEME_STORAGE_KEY), mediaQuery.matches);
      applyTheme(nextTheme);
      setTheme(nextTheme);
    };

    const handleStorage = (event: StorageEvent) => {
      if (event.key === THEME_STORAGE_KEY || event.key === null) syncTheme();
    };
    const handleSystemTheme = () => {
      if (parseStoredTheme(window.localStorage.getItem(THEME_STORAGE_KEY)) === null) syncTheme();
    };

    syncTheme();
    window.addEventListener("storage", handleStorage);
    mediaQuery.addEventListener("change", handleSystemTheme);
    return () => {
      window.removeEventListener("storage", handleStorage);
      mediaQuery.removeEventListener("change", handleSystemTheme);
    };
  }, []);

  const dark = theme === "dark";
  const label = dark ? "Gunakan mode terang" : "Gunakan mode gelap";

  function toggleTheme() {
    const nextTheme: Theme = dark ? "light" : "dark";
    window.localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
    applyTheme(nextTheme);
    setTheme(nextTheme);
  }

  return (
    <button
      type="button"
      className="theme-toggle flex size-10 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-700 shadow-sm hover:border-teal-300 hover:text-teal-800"
      onClick={toggleTheme}
      aria-label={label}
      aria-pressed={dark}
      title={label}
    >
      {dark ? <Sun className="size-4" aria-hidden="true" /> : <Moon className="size-4" aria-hidden="true" />}
    </button>
  );
}
