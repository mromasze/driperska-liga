package pl.romcio.driperska.match.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.romcio.driperska.common.security.CurrentAccount;
import pl.romcio.driperska.match.api.MatchFeedbackDtos.MyFeedback;
import pl.romcio.driperska.match.api.MatchFeedbackDtos.RateableMatch;
import pl.romcio.driperska.match.api.MatchFeedbackDtos.SubmitFeedbackRequest;
import pl.romcio.driperska.match.application.MatchFeedbackService;

@RestController
@RequestMapping("/api/v1/matches")
@PreAuthorize("hasRole('PLAYER')")
public class MatchFeedbackController {
    private final MatchFeedbackService service;

    public MatchFeedbackController(MatchFeedbackService service) {
        this.service = service;
    }

    @GetMapping("/rateable")
    public List<RateableMatch> rateable() {
        return service.rateable(CurrentAccount.require().accountId());
    }

    /** Aggregated peer feedback for a match — visible to any signed-in user (players + admins). */
    @GetMapping("/{id}/feedback-summary")
    @PreAuthorize("isAuthenticated()")
    public pl.romcio.driperska.match.api.MatchFeedbackDtos.MatchFeedbackSummary summary(@PathVariable UUID id) {
        return service.summary(id);
    }

    @PostMapping("/{id}/feedback")
    public MyFeedback submit(@PathVariable UUID id, @Valid @RequestBody SubmitFeedbackRequest req) {
        return service.submit(id, CurrentAccount.require().accountId(),
                req.upvotePlayerId(), req.downvotePlayerId(), req.note());
    }
}
