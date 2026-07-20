package pl.romcio.driperska.season.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.season.application.SeasonService;
import pl.romcio.driperska.season.domain.Season;
import pl.romcio.driperska.season.domain.SeasonStatus;

@RestController
@RequestMapping("/api/v1/seasons")
public class SeasonController {

    private final SeasonService service;

    public SeasonController(SeasonService service) {
        this.service = service;
    }

    @GetMapping
    public List<SeasonResponse> list() {
        return service.list().stream().map(SeasonResponse::from).toList();
    }

    @GetMapping("/current")
    public SeasonResponse current() {
        return SeasonResponse.from(service.current());
    }

    @GetMapping("/{id}")
    public SeasonResponse get(@PathVariable UUID id) {
        return SeasonResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SeasonResponse create(@Valid @RequestBody CreateSeasonRequest req) {
        return SeasonResponse.from(service.create(new Season(req.name(), req.startDate(), req.endDate())));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public SeasonResponse activate(@PathVariable UUID id) {
        return SeasonResponse.from(service.activate(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public SeasonResponse archive(@PathVariable UUID id) {
        return SeasonResponse.from(service.archive(id));
    }

    public record CreateSeasonRequest(
            @NotBlank String name,
            LocalDate startDate,
            LocalDate endDate) {
    }

    public record SeasonResponse(
            UUID id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            SeasonStatus status) {

        static SeasonResponse from(Season s) {
            return new SeasonResponse(s.getId(), s.getName(), s.getStartDate(), s.getEndDate(), s.getStatus());
        }
    }
}
