export type EntityOption = {
  id: string;
  label: string;
  description?: string;
  status?: string;
};

export type LookupState = {
  requestId: number;
  query: string;
  options: EntityOption[];
  selected: EntityOption | null;
  loading: boolean;
  error: string | null;
};

export type LookupAction =
  | { type: "request"; requestId: number; query: string }
  | { type: "success"; requestId: number; options: EntityOption[] }
  | { type: "error"; requestId: number; message: string }
  | { type: "select"; option: EntityOption | null }
  | { type: "reset" };

export const initialLookupState: LookupState = {
  requestId: 0,
  query: "",
  options: [],
  selected: null,
  loading: false,
  error: null
};

export function normalizeLookupQuery(query: string, minimumLength = 2): string | null {
  const normalized = query.trim();
  return normalized.length >= minimumLength ? normalized : null;
}

export function mergeSelectedOption(
  options: readonly EntityOption[],
  selected: EntityOption | null
): EntityOption[] {
  if (!selected) {
    return [...options];
  }
  return [selected, ...options.filter((option) => option.id !== selected.id)];
}

export function reduceLookup(state: LookupState, action: LookupAction): LookupState {
  switch (action.type) {
    case "request":
      return {
        ...state,
        requestId: action.requestId,
        query: action.query,
        loading: true,
        error: null
      };
    case "success":
      if (action.requestId !== state.requestId) {
        return state;
      }
      return {
        ...state,
        options: mergeSelectedOption(action.options, state.selected),
        loading: false,
        error: null
      };
    case "error":
      if (action.requestId !== state.requestId) {
        return state;
      }
      return {
        ...state,
        loading: false,
        error: action.message
      };
    case "select":
      return {
        ...state,
        selected: action.option,
        options: mergeSelectedOption(state.options, action.option),
        error: null
      };
    case "reset":
      return initialLookupState;
  }
}
