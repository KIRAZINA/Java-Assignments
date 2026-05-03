package app.controller;

import app.model.CacheStatsResponse;
import app.model.DataResponse;
import app.service.DataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import app.cache.CacheStats;

@RestController
@RequestMapping("/api")
public class DataController {
    private final DataService dataService;

    public DataController(DataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping("/data")
    public ResponseEntity<DataResponse> getData(@RequestParam("query") String query) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(dataService.fetchData(query));
    }

    @GetMapping("/cache/stats")
    public CacheStatsResponse getCacheStats() {
        CacheStats stats = dataService.getCacheStats();
        return new CacheStatsResponse(stats.getHits(), stats.getMisses(), stats.getHitRate(), stats.getEvictions());
    }

    @DeleteMapping("/cache")
    public ResponseEntity<Void> invalidateCache(@RequestParam("key") String key) {
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        dataService.invalidate(key);
        return ResponseEntity.noContent().build();
    }
}
