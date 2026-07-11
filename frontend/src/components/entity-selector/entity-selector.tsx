"use client";

import { Check, ChevronDown, Loader2, Search, X } from "lucide-react";
import { useId, useMemo, useState } from "react";

import type { EntityOption } from "./entity-selector-model";
import { useEntityLookup } from "@/features/lookups/use-entity-lookup";
import { cn } from "@/lib/utils";

type EntitySelectorProps = {
  value: string;
  onChange: (value: string, option: EntityOption | null) => void;
  loadOptions: (query: string, signal: AbortSignal) => Promise<EntityOption[]>;
  label: string;
  ariaLabel: string;
  placeholder: string;
  selectedOption?: EntityOption | null;
  query?: string;
  onQueryChange?: (query: string) => void;
  disabled?: boolean;
  invalid?: boolean;
  required?: boolean;
};

export function EntitySelector({
  value,
  onChange,
  loadOptions,
  label,
  ariaLabel,
  placeholder,
  selectedOption = null,
  query: controlledQuery,
  onQueryChange,
  disabled = false,
  invalid = false,
  required = false
}: Readonly<EntitySelectorProps>) {
  const [internalQuery, setInternalQuery] = useState(selectedOption?.label ?? "");
  const [editing, setEditing] = useState(false);
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const listboxId = useId();
  const query = controlledQuery ?? (editing ? internalQuery : value ? selectedOption?.label ?? internalQuery : "");
  const selected = value ? selectedOption : null;
  const { state, dispatch } = useEntityLookup({ query, selected, loadOptions, enabled: open && !disabled });
  const options = useMemo(() => state.options, [state.options]);

  function setQuery(nextQuery: string) {
    if (onQueryChange) onQueryChange(nextQuery);
    else setInternalQuery(nextQuery);
  }

  function choose(option: EntityOption) {
    dispatch({ type: "select", option });
    onChange(option.id, option);
    setQuery(option.label);
    setEditing(false);
    setOpen(false);
    setActiveIndex(0);
  }

  function clear() {
    dispatch({ type: "select", option: null });
    onChange("", null);
    setQuery("");
    setEditing(false);
    setOpen(false);
  }

  return (
    <label className="relative grid gap-1.5 text-sm font-bold text-slate-700">
      <span>{label}{required ? " *" : ""}</span>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
        <input
          type="text"
          role="combobox"
          aria-label={ariaLabel}
          aria-expanded={open}
          aria-controls={listboxId}
          aria-autocomplete="list"
          aria-invalid={invalid}
          autoComplete="off"
          className={cn(
            "min-h-11 w-full rounded-lg border bg-white py-2 pl-9 pr-16 font-semibold text-slate-950 outline-none transition focus:ring-2",
            invalid ? "border-red-400 focus:border-red-500 focus:ring-red-100" : "border-slate-300 focus:border-teal-600 focus:ring-teal-100",
            disabled && "cursor-not-allowed bg-slate-100 text-slate-500"
          )}
          value={query}
          placeholder={placeholder}
          disabled={disabled}
          onFocus={() => setOpen(true)}
          onBlur={() => window.setTimeout(() => setOpen(false), 120)}
          onChange={(event) => {
            setEditing(true);
            setQuery(event.target.value);
            if (value) onChange("", null);
            setOpen(true);
            setActiveIndex(0);
          }}
          onKeyDown={(event) => {
            if (event.key === "ArrowDown") {
              event.preventDefault();
              setOpen(true);
              setActiveIndex((current) => Math.min(current + 1, Math.max(options.length - 1, 0)));
            } else if (event.key === "ArrowUp") {
              event.preventDefault();
              setActiveIndex((current) => Math.max(current - 1, 0));
            } else if (event.key === "Enter" && open && options[activeIndex]) {
              event.preventDefault();
              choose(options[activeIndex]);
            } else if (event.key === "Escape") {
              setOpen(false);
            }
          }}
        />
        {query ? (
          <button type="button" className="absolute right-8 top-1/2 -translate-y-1/2 rounded p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-950" onMouseDown={(event) => event.preventDefault()} onClick={clear} aria-label={`Hapus ${ariaLabel}`} disabled={disabled}>
            <X className="size-4" aria-hidden="true" />
          </button>
        ) : null}
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      </div>

      {open ? (
        <div id={listboxId} role="listbox" className="absolute left-0 right-0 top-full z-50 mt-1 h-64 overflow-y-auto rounded-lg border border-slate-200 bg-white p-1 shadow-xl">
          {query.trim().length < 2 ? <p className="p-4 text-center text-sm font-semibold text-slate-500">Ketik minimal 2 karakter.</p> : null}
          {state.loading ? <p className="flex items-center justify-center gap-2 p-4 text-sm font-semibold text-slate-600"><Loader2 className="size-4 animate-spin" />Memuat referensi...</p> : null}
          {state.error ? <p className="p-4 text-center text-sm font-semibold text-red-700">{state.error}</p> : null}
          {!state.loading && !state.error && query.trim().length >= 2 && options.length === 0 ? <p className="p-4 text-center text-sm font-semibold text-slate-500">Data tidak ditemukan.</p> : null}
          {!state.loading && !state.error && query.trim().length >= 2 ? options.map((option, index) => (
            <button
              key={option.id}
              type="button"
              role="option"
              aria-selected={option.id === value}
              className={cn("flex w-full items-start justify-between gap-3 rounded-md px-3 py-2.5 text-left hover:bg-teal-50", index === activeIndex && "bg-teal-50")}
              onMouseDown={(event) => event.preventDefault()}
              onMouseEnter={() => setActiveIndex(index)}
              onClick={() => choose(option)}
            >
              <span className="min-w-0"><span className="block truncate text-sm font-black text-slate-950">{option.label}</span>{option.description ? <span className="mt-0.5 block truncate text-xs font-semibold text-slate-500">{option.description}</span> : null}</span>
              <span className="flex shrink-0 items-center gap-2">{option.status ? <span className="rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-[11px] font-bold text-slate-600">{option.status}</span> : null}{option.id === value ? <Check className="size-4 text-teal-700" /> : null}</span>
            </button>
          )) : null}
        </div>
      ) : null}
    </label>
  );
}
