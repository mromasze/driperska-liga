package pl.romcio.driperska.match.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.romcio.driperska.common.security.CurrentAccount;
import pl.romcio.driperska.match.api.PlannedMatchDtos.CreatePlannedMatchRequest;
import pl.romcio.driperska.match.api.PlannedMatchDtos.CreatePlannedMatchResult;
import pl.romcio.driperska.match.api.PlannedMatchDtos.PlannedMatchResponse;
import pl.romcio.driperska.match.api.PlannedMatchDtos.RsvpRequest;
import pl.romcio.driperska.match.application.PlannedMatchService;

@RestController
@RequestMapping("/api/v1/planned-matches")
public class PlannedMatchController {
    private final PlannedMatchService service;

    public PlannedMatchController(PlannedMatchService service) {
        this.service = service;
    }

    /**
     * Upcoming matches only — a term that has passed cannot be confirmed any more, so it stops being
     * offered. {@code includePast=true} is honoured for the admin schedule page (and ignored for
     * everyone else), which needs the history of what was planned.
     */
    @GetMapping
    public List<PlannedMatchResponse> list(@RequestParam(defaultValue = "false") boolean includePast) {
        var current = CurrentAccount.optional();
        UUID viewer = current.map(a -> a.accountId()).orElse(null);
        boolean staff = current
                .filter(a -> a.isAdmin() || "ROLE_EDITOR".equals(a.role()))
                .isPresent();
        return includePast && staff ? service.listIncludingPast(viewer) : service.listUpcoming(viewer);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public CreatePlannedMatchResult create(@Valid @RequestBody CreatePlannedMatchRequest req) {
        return service.create(req.scheduledAt(), req.note(), CurrentAccount.require().accountId());
    }

    @PostMapping("/{id}/rsvp")
    @PreAuthorize("hasRole('PLAYER')")
    public PlannedMatchResponse rsvp(@PathVariable UUID id, @Valid @RequestBody RsvpRequest req) {
        return service.rsvp(id, CurrentAccount.require().accountId(), req.response());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void cancel(@PathVariable UUID id) {
        service.cancel(id);
    }
}
