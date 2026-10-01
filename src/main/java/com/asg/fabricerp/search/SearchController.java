package com.asg.fabricerp.search;

import com.asg.fabricerp.security.AuthorityChecks;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * <pre>
 *   GET  /search                 the results page (the header's search box, Ctrl+K, links here)
 *   GET  /api/search?q=&kind=    one page of results, for the page and the command palette
 *   GET  /api/search/status      which engine answers, and how the index is doing
 *   POST /api/search/reindex     drop and rebuild the Elasticsearch index
 * </pre>
 *
 * Search itself needs no screen of its own: every signed-in user may search, and sees only what
 * their screens would list. Rebuilding the index is an administrator's job.
 */
@Controller
public class SearchController {

    private final SearchService service;
    private final SearchIndexer indexer;

    public SearchController(SearchService service, SearchIndexer indexer) {
        this.service = service;
        this.indexer = indexer;
    }

    @GetMapping("/search")
    public String page(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("title", "Search");
        model.addAttribute("q", q == null ? "" : q.strip());
        model.addAttribute("kinds", SearchKind.visibleTo(AuthorityChecks.heldAuthorities()));
        model.addAttribute("content", "search/index :: content");
        return "layout/main";
    }

    @GetMapping("/api/search")
    @ResponseBody
    public SearchResult search(@RequestParam(required = false) String q,
                               @RequestParam(required = false) SearchKind kind,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        return service.search(q, kind, page, size);
    }

    @GetMapping("/api/search/status")
    @ResponseBody
    public SearchIndexer.Status status() {
        return indexer.status();
    }

    @PostMapping("/api/search/reindex")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
    public Map<String, Object> reindex() {
        long pushed = indexer.rebuild();
        return Map.of("indexed", pushed, "status", indexer.status());
    }
}
