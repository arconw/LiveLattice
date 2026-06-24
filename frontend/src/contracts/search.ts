import type { GatewayClient } from "./api-client";

export const searchResultTypes = ["canvas", "comment", "document", "dashboard", "template", "user"] as const;

export type SearchResultType = (typeof searchResultTypes)[number];

export type SearchResult = {
  id: string;
  type: SearchResultType;
  workspaceId: string;
  title: string | null;
  content: string | null;
  tags: string[];
  authorId?: string;
  createdAt?: string;
  updatedAt?: string;
  highlights: Record<string, string[]>;
  targetUrl?: string;
};

export type SearchResponse = {
  results: SearchResult[];
  total: number;
  page: number;
  size: number;
  nextSearchAfter: string | null;
  facets: Record<string, Record<string, number>>;
};

export type SearchSuggestion = {
  value: string;
  type?: SearchResultType;
  resultId?: string;
};

export type SearchRequest = {
  q: string;
  workspaceId: string;
  types?: SearchResultType[];
  tags?: string[];
  from?: string;
  to?: string;
  size?: number;
  searchAfter?: string | null;
};

export function buildSearchPath(request: SearchRequest) {
  const params = new URLSearchParams();
  params.set("q", request.q);
  params.set("workspace_id", request.workspaceId);
  params.set("size", String(request.size ?? 20));

  request.types?.forEach((type) => params.append("type", type));
  request.tags?.forEach((tag) => params.append("tags", tag));

  if (request.from) {
    params.set("from", request.from);
  }

  if (request.to) {
    params.set("to", request.to);
  }

  if (request.searchAfter) {
    params.set("search_after", request.searchAfter);
  }

  return `/api/search/search?${params.toString()}`;
}

export function buildSuggestPath(q: string, workspaceId: string) {
  const params = new URLSearchParams({ q, workspace_id: workspaceId });
  return `/api/search/search/suggest?${params.toString()}`;
}

export async function searchWorkspace(client: GatewayClient, request: SearchRequest, signal?: AbortSignal) {
  const payload = await client.get<unknown>(buildSearchPath(request), { signal });
  return mapSearchResponse(payload);
}

export async function suggestWorkspaceSearch(client: GatewayClient, q: string, workspaceId: string, signal?: AbortSignal) {
  const payload = await client.get<unknown>(buildSuggestPath(q, workspaceId), { signal });
  return mapSearchSuggestions(payload);
}

export function mapSearchResponse(payload: unknown): SearchResponse {
  const record = asRecord(payload);
  const results = Array.isArray(record.results) ? record.results.map(mapSearchResult).filter((result): result is SearchResult => result !== null) : [];

  return {
    results,
    total: toNumber(record.total, results.length),
    page: toNumber(record.page, 1),
    size: toNumber(record.size, results.length),
    nextSearchAfter: typeof record.nextSearchAfter === "string" && record.nextSearchAfter.length > 0 ? record.nextSearchAfter : null,
    facets: mapFacets(record.facets)
  };
}

export function mapSearchSuggestions(payload: unknown): SearchSuggestion[] {
  if (Array.isArray(payload)) {
    return payload.flatMap((item) => mapSuggestion(item));
  }

  const record = asRecord(payload);
  const suggestions = Array.isArray(record.suggestions) ? record.suggestions : [];
  return suggestions.flatMap((item) => mapSuggestion(item));
}

export function highlightToParts(value: string) {
  const parts: Array<{ text: string; highlighted: boolean }> = [];
  const pattern = /<\/?em>/gi;
  let highlighted = false;
  let cursor = 0;
  let match = pattern.exec(value);

  while (match) {
    if (match.index > cursor) {
      parts.push({ text: value.slice(cursor, match.index), highlighted });
    }

    highlighted = match[0].toLowerCase() === "<em>";
    cursor = match.index + match[0].length;
    match = pattern.exec(value);
  }

  if (cursor < value.length) {
    parts.push({ text: value.slice(cursor), highlighted });
  }

  return parts.length > 0 ? parts : [{ text: value, highlighted: false }];
}

function mapSearchResult(payload: unknown): SearchResult | null {
  const record = asRecord(payload);
  const type = normalizeResultType(record.type);
  const id = toString(record.id);
  const workspaceId = toString(record.workspaceId ?? record.workspace_id);

  if (!type || !id || !workspaceId) {
    return null;
  }

  return {
    id,
    type,
    workspaceId,
    title: toNullableString(record.title),
    content: toNullableString(record.content ?? record.contentText ?? record.content_text),
    tags: Array.isArray(record.tags) ? record.tags.flatMap((tag) => toString(tag) ? [toString(tag)] : []) : [],
    authorId: toOptionalString(record.authorId ?? record.author_id),
    createdAt: toOptionalString(record.createdAt ?? record.created_at),
    updatedAt: toOptionalString(record.updatedAt ?? record.updated_at),
    highlights: mapHighlights(record.highlights),
    targetUrl: toOptionalString(record.targetUrl ?? record.target_url)
  };
}

function mapSuggestion(payload: unknown): SearchSuggestion[] {
  if (typeof payload === "string") {
    return [{ value: payload }];
  }

  const record = asRecord(payload);
  const value = toString(record.value ?? record.text ?? record.title);

  if (!value) {
    return [];
  }

  const type = normalizeResultType(record.type);
  const resultId = toOptionalString(record.resultId ?? record.result_id ?? record.id);

  return [{ value, type: type ?? undefined, resultId }];
}

function mapFacets(payload: unknown) {
  const record = asRecord(payload);
  const facets: Record<string, Record<string, number>> = {};

  Object.entries(record).forEach(([facetName, values]) => {
    const valueRecord = asRecord(values);
    facets[facetName] = {};

    Object.entries(valueRecord).forEach(([value, count]) => {
      facets[facetName][value] = toNumber(count, 0);
    });
  });

  return facets;
}

function mapHighlights(payload: unknown) {
  const record = asRecord(payload);
  const highlights: Record<string, string[]> = {};

  Object.entries(record).forEach(([field, snippets]) => {
    highlights[field] = Array.isArray(snippets) ? snippets.flatMap((snippet) => toString(snippet) ? [toString(snippet)] : []) : [];
  });

  return highlights;
}

function normalizeResultType(value: unknown): SearchResultType | null {
  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.toLowerCase();
  return searchResultTypes.find((type) => type === normalized) ?? null;
}

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function toNumber(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function toString(value: unknown) {
  return typeof value === "string" ? value : "";
}

function toOptionalString(value: unknown) {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function toNullableString(value: unknown) {
  return typeof value === "string" ? value : null;
}
