package pl.romcio.driperska.match.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.common.security.CurrentAccount;
import pl.romcio.driperska.common.web.PageResponse;
import pl.romcio.driperska.match.api.MatchDtos.*;
import pl.romcio.driperska.match.application.*;
import pl.romcio.driperska.match.application.DrawService.DrawResult;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchStatus;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {
    private final MatchService matchService;
    private final DrawLobbyService drawLobbyService;
    private final ResultService resultService;
    private final ApprovalService approvalService;
    private final MatchAssembler assembler;

    public MatchController(MatchService matchService, DrawLobbyService drawLobbyService,
                           ResultService resultService, ApprovalService approvalService,
                           MatchAssembler assembler) {
        this.matchService = matchService;
        this.drawLobbyService = drawLobbyService;
        this.resultService = resultService;
        this.approvalService = approvalService;
        this.assembler = assembler;
    }

    @GetMapping
    public PageResponse<MatchSummaryResponse> list(
            @RequestParam(required = false) MatchStatus status,
            @RequestParam(required = false) UUID seasonId, Pageable pageable) {
        MatchStatus effective = CurrentAccount.optional().isPresent() ? status : MatchStatus.APPROVED;
        return PageResponse.of(matchService.list(effective, seasonId, pageable).map(assembler::toSummary));
    }

    @GetMapping("/{id}")
    public MatchResponse get(@PathVariable UUID id) {
        Match match = matchService.get(id);
        if (CurrentAccount.optional().isEmpty() && match.getStatus() != MatchStatus.APPROVED) {
            throw ResourceNotFoundException.of("Match", id);
        }
        return assembler.toResponse(match);
    }

    @GetMapping("/{id}/events")
    public java.util.List<MatchEventResponse> events(@PathVariable UUID id) {
        return matchService.events(id).stream()
                .map(e -> new MatchEventResponse(e.getType(), e.getActorAccountId(),
                        e.getPayloadJson(), e.getCreatedAt())).toList();
    }

    /** Creating a match immediately opens round one for all ten logged-in players. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchResponse create(@Valid @RequestBody CreateMatchRequest req) {
        UUID actor = CurrentAccount.require().accountId();
        Match match = matchService.create(req.seasonId(), req.drawMode(), req.playerIds(), actor);
        drawLobbyService.adminDraw(match.getId(), actor);
        return assembler.toResponse(matchService.get(match.getId()));
    }

    @PostMapping("/{id}/draw")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public DrawResponse draw(@PathVariable UUID id) {
        UUID actor = CurrentAccount.require().accountId();
        return toDrawResponse(id, drawLobbyService.adminDraw(id, actor));
    }

    @PostMapping("/{id}/draw/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchResponse confirmDraw(@PathVariable UUID id) {
        return assembler.toResponse(drawLobbyService.adminConfirm(
                id, CurrentAccount.require().accountId()));
    }

    @PostMapping("/{id}/results")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchResponse submitResults(@PathVariable UUID id, @Valid @RequestBody SubmitResultsRequest req) {
        return assembler.toResponse(resultService.saveResults(
                id, req, CurrentAccount.require().accountId()));
    }

    @PatchMapping("/{id}/results")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchResponse editResults(@PathVariable UUID id, @Valid @RequestBody SubmitResultsRequest req) {
        return assembler.toResponse(resultService.saveResults(
                id, req, CurrentAccount.require().accountId()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest req) {
        return assembler.toResponse(approvalService.approve(
                id, req, CurrentAccount.require().accountId()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        return assembler.toResponse(approvalService.reject(
                id, req, CurrentAccount.require().accountId()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse cancel(@PathVariable UUID id) {
        return assembler.toResponse(matchService.cancel(id, CurrentAccount.require().accountId()));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse reopen(@PathVariable UUID id) {
        return assembler.toResponse(approvalService.reopen(
                id, CurrentAccount.require().accountId()));
    }

    private DrawResponse toDrawResponse(UUID matchId, DrawResult result) {
        var blue = result.slots().stream().filter(s -> s.side() == Side.BLUE)
                .map(s -> new DrawSlotResponse(s.playerId(), s.nickname(), s.role(), s.mmr())).toList();
        var red = result.slots().stream().filter(s -> s.side() == Side.RED)
                .map(s -> new DrawSlotResponse(s.playerId(), s.nickname(), s.role(), s.mmr())).toList();
        Match match = matchService.get(matchId);
        return new DrawResponse(matchId, match.getDrawMode(), blue, red,
                new BalanceResponse(result.blueMmrAvg(), result.redMmrAvg(), result.predictedBlueWinPct()));
    }
}