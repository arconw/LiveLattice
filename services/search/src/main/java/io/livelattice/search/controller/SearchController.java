package io.livelattice.search.controller;

import io.livelattice.search.dto.IndexStatus;
import io.livelattice.search.dto.ReindexResponse;
import io.livelattice.search.dto.SearchResponse;
import io.livelattice.search.dto.SuggestResponse;
import io.livelattice.search.model.SearchCriteria;
import io.livelattice.search.opensearch.IndexManager;
import io.livelattice.search.service.ReindexService;
import io.livelattice.search.service.SearchRequestParser;
import io.livelattice.search.service.SearchService;
import io.livelattice.search.service.SuggestService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
public class SearchController {

    private final SearchService searchService;
    private final SuggestService suggestService;
    private final SearchRequestParser parser;
    private final IndexManager indexManager;
    private final ReindexService reindexService;

    public SearchController(SearchService searchService,
                            SuggestService suggestService,
                            SearchRequestParser parser,
                            IndexManager indexManager,
                            ReindexService reindexService) {
        this.searchService = searchService;
        this.suggestService = suggestService;
        this.parser = parser;
        this.indexManager = indexManager;
        this.reindexService = reindexService;
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam("q") @NotBlank @Size(max = 512) String query,
                                 @RequestParam(value = "type", required = false) String type,
                                 @RequestParam(value = "workspace_id", required = false) String workspaceId,
                                 @RequestParam(value = "tags", required = false) String tags,
                                 @RequestParam(value = "from", required = false) String from,
                                 @RequestParam(value = "to", required = false) String to,
                                 @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
                                 @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
                                 @RequestParam(value = "search_after", required = false) String searchAfter) {
        SearchCriteria criteria = parser.parse(query, type, workspaceId, tags, from, to, page, size, searchAfter);
        return searchService.search(criteria);
    }

    @GetMapping("/search/suggest")
    public SuggestResponse suggest(@RequestParam("q") @NotBlank @Size(max = 128) String query,
                                   @RequestParam(value = "workspace_id", required = false) String workspaceId) {
        return suggestService.suggest(query, workspaceId);
    }

    @PostMapping("/search/reindex")
    public ReindexResponse reindex(@RequestHeader Map<String, String> headers) {
        return reindexService.trigger(headers);
    }

    @GetMapping("/search/indices")
    public List<IndexStatus> indices() {
        return indexManager.indexStatuses();
    }
}
