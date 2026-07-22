package pl.romcio.driperska.highlight.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;
import pl.romcio.driperska.highlight.application.HighlightService;
import pl.romcio.driperska.highlight.application.HighlightService.HighlightVideo;

@RestController
@RequestMapping("/api/v1/highlights")
public class HighlightController {
    private final HighlightService service;

    public HighlightController(HighlightService service) {
        this.service = service;
    }

    @GetMapping
    public List<HighlightVideo> list() {
        return service.list();
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public HighlightVideo upload(@RequestParam("file") MultipartFile file) {
        return service.store(file);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
