"use client";

import { useEffect, useReducer, useRef } from "react";

import {
  initialLookupState,
  normalizeLookupQuery,
  reduceLookup,
  type EntityOption
} from "@/components/entity-selector/entity-selector-model";

type UseEntityLookupInput = {
  query: string;
  selected: EntityOption | null;
  loadOptions: (query: string, signal: AbortSignal) => Promise<EntityOption[]>;
  enabled: boolean;
};

export function useEntityLookup({ query, selected, loadOptions, enabled }: UseEntityLookupInput) {
  const [state, dispatch] = useReducer(reduceLookup, { ...initialLookupState, selected });
  const requestIdRef = useRef(0);

  useEffect(() => {
    dispatch({ type: "select", option: selected });
  }, [selected]);

  useEffect(() => {
    const normalized = normalizeLookupQuery(query);
    if (!enabled || !normalized) {
      const requestId = ++requestIdRef.current;
      dispatch({ type: "request", requestId, query: "" });
      dispatch({ type: "success", requestId, options: [] });
      return;
    }

    const requestId = ++requestIdRef.current;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      dispatch({ type: "request", requestId, query: normalized });
      void loadOptions(normalized, controller.signal)
        .then((options) => dispatch({ type: "success", requestId, options }))
        .catch((error: unknown) => {
          if (controller.signal.aborted) return;
          dispatch({
            type: "error",
            requestId,
            message: error instanceof Error ? error.message : "Data referensi tidak dapat dimuat."
          });
        });
    }, 300);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [enabled, loadOptions, query]);

  return { state, dispatch };
}
