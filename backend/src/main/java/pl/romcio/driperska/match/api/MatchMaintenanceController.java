package pl.romcio.driperska.match.api;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.romcio.driperska.common.security.CurrentAccount;
import pl.romcio.driperska.match.application.MatchMaintenanceService;
import pl.romcio.driperska.match.application.MatchMaintenanceService.Summary;

/**
 * Admin-only bulk housekeeping for the match list.
 *
 * <p>Mapped under {@code /api/v1/admin/**} rather than {@code /api/v1/matches/**} on purpose: the
 * security config makes {@code GET /api/v1/matches/**} public for the site's read-only pages, so a
 * counts endpoint under that prefix would have been readable by anyone.
 */
@RestController
@RequestMapping("/api/v1/admin/matches")
@PreAuthorize("hasRole('ADMIN')")
public class MatchMaintenanceController {

    private final MatchMaintenanceService maintenance;

    public MatchMaintenanceController(MatchMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    /** Counts behind each button, so the UI can label them and disable the ones with nothing to do. */
    @GetMapping("/maintenance")
    public Summary summary() {
        return maintenance.summary();
    }

    /** Cancels every match still in flight. Rows are kept as CANCELLED. */
    @PostMapping("/maintenance/stop-all")
    public AffectedResponse stopAll() {
        return new AffectedResponse(maintenance.stopAllRunning(CurrentAccount.require().accountId()));
    }

    /** Deletes every match whose champion draft had already started. Irreversible. */
    @PostMapping("/maintenance/delete-drafts-in-progress")
    public AffectedResponse deleteDraftsInProgress() {
        return new AffectedResponse(
                maintenance.deleteDraftsInProgress(CurrentAccount.require().accountId()));
    }

    /** Deletes everything that is not APPROVED, leaving only the confirmed record. Irreversible. */
    @PostMapping("/maintenance/purge-unapproved")
    public AffectedResponse purgeUnapproved() {
        return new AffectedResponse(maintenance.purgeUnapproved(CurrentAccount.require().accountId()));
    }

    /** Deletes a single match outright, whatever its status. Irreversible. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        maintenance.delete(id, CurrentAccount.require().accountId());
    }

    /** @param affected how many matches the operation touched */
    public record AffectedResponse(int affected) {}
}
