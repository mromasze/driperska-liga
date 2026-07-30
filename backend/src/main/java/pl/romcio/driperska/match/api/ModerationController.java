package pl.romcio.driperska.match.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.romcio.driperska.common.security.CurrentAccount;
import pl.romcio.driperska.common.web.PageResponse;
import pl.romcio.driperska.match.api.MatchDtos.ManualSlotRequest;
import pl.romcio.driperska.match.api.MatchDtos.MatchResponse;
import pl.romcio.driperska.match.api.MatchDtos.SubmitResultsRequest;
import pl.romcio.driperska.match.api.ModerationDtos.CreateSubmissionRequest;
import pl.romcio.driperska.match.api.ModerationDtos.SubmissionResponse;
import pl.romcio.driperska.match.api.ModerationDtos.UpdateSubmissionRequest;
import pl.romcio.driperska.match.application.MatchAssembler;
import pl.romcio.driperska.match.application.MatchOcrService;
import pl.romcio.driperska.match.application.MatchSubmissionService;
import pl.romcio.driperska.match.application.MatchSubmissionService.RosterSlot;

/**
 * The moderator's own little panel: record a played match, fill its statistics (by hand or from
 * screenshots) and send it to the admin approval queue — nothing else.
 *
 * <p>Authorisation is a database check on every call ({@link MatchSubmissionService#requireModerator})
 * rather than a role in the access token, so granting or revoking the permission takes effect
 * immediately instead of at the next token refresh. Ownership is checked per submission, so this
 * endpoint can never touch a match run by an admin through the live pipeline.
 */
@RestController
@RequestMapping("/api/v1/moderation/matches")
@PreAuthorize("isAuthenticated()")
public class ModerationController {

    private static final int MAX_PAGE_SIZE = 50;

    private final MatchSubmissionService submissions;
    private final MatchOcrService ocrService;
    private final MatchAssembler assembler;

    public ModerationController(MatchSubmissionService submissions, MatchOcrService ocrService,
                                MatchAssembler assembler) {
        this.submissions = submissions;
        this.ocrService = ocrService;
        this.assembler = assembler;
    }

    /** My submissions, newest played first. Cancelled ones are not listed. */
    @GetMapping
    public PageResponse<SubmissionResponse> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.of(submissions
                .listOwn(CurrentAccount.require().accountId(), pageable)
                .map(SubmissionResponse::from));
    }

    /** Step one: the roster of a game that has already been played. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchResponse create(@Valid @RequestBody CreateSubmissionRequest req) {
        var match = submissions.create(CurrentAccount.require().accountId(),
                req.seasonId(), req.playedAt(), toRoster(req.teams()));
        return assembler.toResponse(match);
    }

    /** Corrects the date of play and, before the results are queued, the roster. */
    @PatchMapping("/{id}")
    public MatchResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSubmissionRequest req) {
        var match = submissions.update(CurrentAccount.require().accountId(), id,
                req.playedAt(), req.teams() == null ? null : toRoster(req.teams()));
        return assembler.toResponse(match);
    }

    /** Step two: statistics → approval queue. Re-sending the same submission just updates it. */
    @PostMapping("/{id}/results")
    public MatchResponse submitResults(@PathVariable UUID id,
                                       @Valid @RequestBody SubmitResultsRequest req) {
        var match = submissions.saveResults(CurrentAccount.require().accountId(), id, req);
        return assembler.toResponse(match);
    }

    /** Reads end-game screenshots into an editable draft — same AI pass the admin panel uses. */
    @PostMapping(value = "/{id}/results/ocr", consumes = "multipart/form-data")
    public MatchOcrService.OcrDraft ocrResults(@PathVariable UUID id,
                                              @RequestParam("files") List<MultipartFile> files) {
        UUID matchId = submissions.requireOwnEditable(CurrentAccount.require().accountId(), id);
        return ocrService.extract(matchId, files);
    }

    /** Withdraws a submission that has not been approved yet. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        submissions.cancel(CurrentAccount.require().accountId(), id);
    }

    private static List<RosterSlot> toRoster(List<ManualSlotRequest> teams) {
        return teams.stream().map(s -> new RosterSlot(s.playerId(), s.side(), s.role())).toList();
    }
}
