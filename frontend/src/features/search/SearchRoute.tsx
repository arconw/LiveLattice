import { FileSearch, Loader2, Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Link, useOutletContext, useParams, useSearchParams } from "react-router-dom";
import { AppError, createWorkspaceCacheKey } from "../../contracts/api-client";
import { searchResponseFixture, searchSuggestionsFixture } from "../../contracts/fixtures";
import { highlightToParts, searchResultTypes, searchWorkspace, suggestWorkspaceSearch } from "../../contracts/search";
import type { SearchResponse, SearchResult, SearchResultType, SearchSuggestion } from "../../contracts/search";
import { Badge, Button, EmptyState, ErrorState, Input, LoadingState, Panel, StatusChip } from "../../design-system/components";
import { useAuth } from "../auth/AuthProvider";
import type { ShellOutletContext } from "../shell/AppShell";

type SearchStatus = "idle" | "loading" | "ready" | "empty" | "error";
type SuggestStatus = "idle" | "loading" | "ready" | "empty";

export function SearchRoute() {
  const { workspaceSlug = "factory-floor" } = useParams();
  const outlet = useOutletContext<ShellOutletContext>();
  const auth = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const workspaceId = outlet.activeWorkspace?.id ?? searchResponseFixture.results[0]?.workspaceId ?? workspaceSlug;
  const initialQuery = searchParams.get("q") ?? "diagram RBAC export";
  const [query, setQuery] = useState(initialQuery);
  const [response, setResponse] = useState<SearchResponse>(searchResponseFixture);
  const [status, setStatus] = useState<SearchStatus>(initialQuery ? "ready" : "idle");
  const [error, setError] = useState<AppError | null>(null);
  const [suggestions, setSuggestions] = useState<SearchSuggestion[]>(searchSuggestionsFixture);
  const [suggestStatus, setSuggestStatus] = useState<SuggestStatus>("ready");
  const skipAfterEffectRef = useRef<string | null>(null);
  const selectedTypes = useMemo(() => searchParams.getAll("type").filter((type): type is SearchResultType => searchResultTypes.includes(type as SearchResultType)), [searchParams]);
  const selectedTags = useMemo(() => searchParams.getAll("tag"), [searchParams]);
  const from = searchParams.get("from") ?? "";
  const to = searchParams.get("to") ?? "";
  const searchAfter = searchParams.get("after");
  const cacheKey = createWorkspaceCacheKey(workspaceSlug, "search", query, selectedTypes.join(","), selectedTags.join(","), searchAfter);

  const executeSearch = useCallback(
    async (nextSearchAfter: string | null, append: boolean, signal?: AbortSignal) => {
      const trimmedQuery = query.trim();

      if (!trimmedQuery) {
        setStatus("idle");
        setResponse({ ...searchResponseFixture, results: [], total: 0, nextSearchAfter: null });
        return;
      }

      setStatus("loading");
      setError(null);

      try {
        const nextResponse = await searchWorkspace(
          auth.client,
          {
            q: trimmedQuery,
            workspaceId,
            types: selectedTypes,
            tags: selectedTags,
            from: from || undefined,
            to: to || undefined,
            size: 20,
            searchAfter: nextSearchAfter
          },
          signal
        );

        setResponse((current) => ({
          ...nextResponse,
          results: append ? [...current.results, ...nextResponse.results] : nextResponse.results
        }));
        setStatus(nextResponse.results.length === 0 && !append ? "empty" : "ready");
      } catch (loadError) {
        if (loadError instanceof AppError && loadError.code === "REQUEST_ABORTED") {
          return;
        }

        setError(loadError instanceof AppError ? loadError : new AppError({ status: 0, code: "SEARCH_FAILED", message: "Search could not be completed.", retryable: true }));
        setStatus("error");
      }
    },
    [auth.client, from, query, selectedTags, selectedTypes, to, workspaceId]
  );

  useEffect(() => {
    if (searchAfter && skipAfterEffectRef.current === searchAfter) {
      skipAfterEffectRef.current = null;
      return;
    }

    const controller = new AbortController();
    void executeSearch(searchAfter, Boolean(searchAfter), controller.signal);
    return () => controller.abort();
  }, [executeSearch, searchAfter]);

  useEffect(() => {
    const trimmedQuery = query.trim();

    if (trimmedQuery.length < 2) {
      setSuggestions([]);
      setSuggestStatus(trimmedQuery ? "empty" : "idle");
      return undefined;
    }

    setSuggestStatus("loading");
    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      suggestWorkspaceSearch(auth.client, trimmedQuery, workspaceId, controller.signal)
        .then((items) => {
          setSuggestions(items);
          setSuggestStatus(items.length > 0 ? "ready" : "empty");
        })
        .catch((suggestError) => {
          if (suggestError instanceof AppError && suggestError.code === "REQUEST_ABORTED") {
            return;
          }

          setSuggestions([]);
          setSuggestStatus("empty");
        });
    }, 250);

    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [auth.client, query, workspaceId]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = new URLSearchParams(searchParams);
    next.set("q", query.trim());
    next.delete("after");
    setSearchParams(next);
  }

  function chooseSuggestion(suggestion: SearchSuggestion) {
    setQuery(suggestion.value);
    const next = new URLSearchParams(searchParams);
    next.set("q", suggestion.value);
    next.delete("after");
    setSearchParams(next);
  }

  function toggleType(type: SearchResultType) {
    const next = new URLSearchParams(searchParams);
    const values = next.getAll("type");
    next.delete("type");
    if (values.includes(type)) {
      values.filter((value) => value !== type).forEach((value) => next.append("type", value));
    } else {
      [...values, type].forEach((value) => next.append("type", value));
    }
    next.delete("after");
    setSearchParams(next);
  }

  function toggleTag(tag: string) {
    const next = new URLSearchParams(searchParams);
    const values = next.getAll("tag");
    next.delete("tag");
    if (values.includes(tag)) {
      values.filter((value) => value !== tag).forEach((value) => next.append("tag", value));
    } else {
      [...values, tag].forEach((value) => next.append("tag", value));
    }
    next.delete("after");
    setSearchParams(next);
  }

  function applyDates(nextFrom: string, nextTo: string) {
    const next = new URLSearchParams(searchParams);
    if (nextFrom) {
      next.set("from", nextFrom);
    } else {
      next.delete("from");
    }

    if (nextTo) {
      next.set("to", nextTo);
    } else {
      next.delete("to");
    }
    next.delete("after");
    setSearchParams(next);
  }

  async function loadNextPage() {
    if (!response.nextSearchAfter) {
      return;
    }

    const token = response.nextSearchAfter;
    skipAfterEffectRef.current = token;
    await executeSearch(token, true);
    const next = new URLSearchParams(searchParams);
    next.set("after", token);
    setSearchParams(next, { replace: true });
  }

  return (
    <section className="feature-route search-route" aria-labelledby="search-route-title">
      <div className="route-heading">
        <span className="kicker">OpenSearch command layer</span>
        <h1 id="search-route-title">Workspace search</h1>
        <p>Search results keep highlights, facets, selected filters, and the next search_after token scoped to the active workspace.</p>
      </div>

      <form className="search-command-panel" onSubmit={submit}>
        <label className="field-label" htmlFor="workspace-search-query">
          Search query
        </label>
        <div className="search-input-row">
          <Input id="workspace-search-query" value={query} onChange={(event) => setQuery(event.target.value)} autoComplete="off" />
          <Button variant="primary" type="submit" icon={status === "loading" ? <Loader2 size={16} aria-hidden="true" /> : <Search size={16} aria-hidden="true" />}>
            Search
          </Button>
        </div>
        <div className="suggestion-list" role="listbox" aria-label="Search suggestions" aria-busy={suggestStatus === "loading"}>
          {suggestStatus === "loading" ? <span className="small-copy">Loading suggestions</span> : null}
          {suggestStatus === "empty" ? <span className="small-copy">No suggestions</span> : null}
          {suggestions.map((suggestion) => (
            <button className="suggestion-item" type="button" role="option" key={`${suggestion.value}-${suggestion.resultId ?? "suggestion"}`} onClick={() => chooseSuggestion(suggestion)}>
              <FileSearch size={14} aria-hidden="true" />
              <span>{suggestion.value}</span>
              {suggestion.type ? <Badge tone="info">{suggestion.type}</Badge> : null}
            </button>
          ))}
        </div>
      </form>

      <div className="feature-grid search-layout">
        <Panel className="facet-panel" as="aside">
          <div className="panel-heading-row">
            <div>
              <span className="kicker">Facets</span>
              <h2>Filter results</h2>
            </div>
            <StatusChip tone="info">{response.total} total</StatusChip>
          </div>
          <FacetGroup title="Type" entries={response.facets.type ?? {}} selected={selectedTypes} onToggle={(value) => toggleType(value as SearchResultType)} />
          <FacetGroup title="Tags" entries={response.facets.tags ?? {}} selected={selectedTags} onToggle={toggleTag} />
          <DateFilters from={from} to={to} onApply={applyDates} />
          <div className="key-value">
            <span>Cache key</span>
            <strong>{cacheKey.join(" / ")}</strong>
          </div>
          <div className="key-value">
            <span>nextSearchAfter</span>
            <strong>{response.nextSearchAfter ?? "none"}</strong>
          </div>
        </Panel>

        <Panel className="search-results-panel" as="section">
          {status === "loading" ? <LoadingState label="Searching workspace graph" /> : null}
          {status === "idle" ? <EmptyState title="Start a workspace search" copy="Enter a query to search canvases, dashboards, documents, comments, templates, and people." /> : null}
          {status === "empty" ? <EmptyState title="No results" copy="No indexed workspace objects match the current query and filters." /> : null}
          {status === "error" && error ? <ErrorState title="Search failed" copy={error.message} requestId={error.requestId} /> : null}
          {status === "ready" ? (
            <div className="search-result-list">
              {response.results.map((result) => (
                <SearchResultCard result={result} workspaceSlug={workspaceSlug} key={`${result.type}-${result.id}`} />
              ))}
              {response.nextSearchAfter ? (
                <Button variant="secondary" onClick={() => void loadNextPage()}>
                  Load next page
                </Button>
              ) : null}
            </div>
          ) : null}
        </Panel>
      </div>
    </section>
  );
}

function FacetGroup({ title, entries, selected, onToggle }: { title: string; entries: Record<string, number>; selected: string[]; onToggle: (value: string) => void }) {
  return (
    <fieldset className="facet-group">
      <legend>{title}</legend>
      {Object.keys(entries).length === 0 ? <span className="small-copy">No {title.toLowerCase()} facets</span> : null}
      {Object.entries(entries).map(([value, count]) => (
        <label className="facet-option" key={value}>
          <input type="checkbox" checked={selected.includes(value)} onChange={() => onToggle(value)} />
          <span>{value}</span>
          <Badge tone={selected.includes(value) ? "info" : "neutral"}>{count}</Badge>
        </label>
      ))}
    </fieldset>
  );
}

function DateFilters({ from, to, onApply }: { from: string; to: string; onApply: (from: string, to: string) => void }) {
  const [nextFrom, setNextFrom] = useState(from);
  const [nextTo, setNextTo] = useState(to);

  useEffect(() => {
    setNextFrom(from);
    setNextTo(to);
  }, [from, to]);

  return (
    <fieldset className="facet-group">
      <legend>Date range</legend>
      <label className="form-field" htmlFor="search-from">
        <span className="field-label">From</span>
        <Input id="search-from" type="date" value={nextFrom} onChange={(event) => setNextFrom(event.target.value)} />
      </label>
      <label className="form-field" htmlFor="search-to">
        <span className="field-label">To</span>
        <Input id="search-to" type="date" value={nextTo} onChange={(event) => setNextTo(event.target.value)} />
      </label>
      <Button variant="secondary" onClick={() => onApply(nextFrom, nextTo)}>
        Apply dates
      </Button>
    </fieldset>
  );
}

function SearchResultCard({ result, workspaceSlug }: { result: SearchResult; workspaceSlug: string }) {
  const href = result.targetUrl ?? `/w/${workspaceSlug}/search?q=${encodeURIComponent(result.title ?? result.id)}`;
  const snippets = Object.values(result.highlights).flat();

  return (
    <article className="search-result-card">
      <div className="result-card-heading">
        <div>
          <Badge tone={result.type === "comment" ? "warning" : result.type === "dashboard" ? "healthy" : "info"}>{result.type}</Badge>
          <h2>{result.title ?? "Untitled result"}</h2>
        </div>
        <Link className="button button-secondary" to={href}>
          Open
        </Link>
      </div>
      <p className="small-copy">{result.content ?? "No preview text indexed."}</p>
      {snippets.length > 0 ? (
        <div className="highlight-list" aria-label="Highlighted snippets">
          {snippets.map((snippet, index) => (
            <p className="highlight-snippet" key={`${result.id}-snippet-${index}`}>
              {highlightToParts(snippet).map((part, partIndex) => (part.highlighted ? <mark key={`${part.text}-${partIndex}`}>{part.text}</mark> : <span key={`${part.text}-${partIndex}`}>{part.text}</span>))}
            </p>
          ))}
        </div>
      ) : null}
      <div className="tag-row">
        {result.tags.map((tag) => (
          <Badge tone="neutral" key={tag}>
            {tag}
          </Badge>
        ))}
      </div>
    </article>
  );
}
