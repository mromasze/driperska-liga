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

    @GetMapping
    public List<PlannedMatchResponse> list() {
        UUID viewer = CurrentAccount.optional().map(a -> a.accountId()).orElse(null);
        return service.listUpcoming(viewer);
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
