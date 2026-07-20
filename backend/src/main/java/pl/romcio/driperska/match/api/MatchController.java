package pl.romcio.driperska.match.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.common.security.CurrentAccount;
import pl.romcio.driperska.common.web.PageResponse;
import pl.romcio.driperska.match.api.MatchDtos.ApproveRequest;
import pl.romcio.driperska.match.api.MatchDtos.BalanceResponse;
import pl.romcio.driperska.match.api.MatchDtos.CreateMatchRequest;
import pl.romcio.driperska.match.api.MatchDtos.DrawResponse;
import pl.romcio.driperska.match.api.MatchDtos.DrawSlotResponse;
import pl.romcio.driperska.match.api.MatchDtos.MatchEventResponse;
import pl.romcio.driperska.match.api.MatchDtos.MatchResponse;
import pl.romcio.driperska.match.api.MatchDtos.MatchSummaryResponse;
import pl.romcio.driperska.match.api.MatchDtos.RejectRequest;
import pl.romcio.driperska.match.api.MatchDtos.SubmitResultsRequest;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.match.application.ApprovalService;
import pl.romcio.driperska.match.application.DrawService;
import pl.romcio.driperska.match.application.DrawService.DrawResult;
import pl.romcio.driperska.match.application.MatchAssembler;
import pl.romcio.driperska.match.application.MatchService;
import pl.romcio.driperska.match.application.ResultService;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchStatus;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

    private final MatchService matchService;
    private final DrawService drawService;
    private final ResultService resultService;
    private final ApprovalService approvalService;
    private final MatchAssembler assembler;

    public MatchController(MatchService matchService, DrawService drawService,
                           ResultService resultService, ApprovalService approvalService,
                           MatchAssembler assembler) {
        this.matchService = matchService;
        this.drawService = drawService;
        this.resultService = resultService;
        this.approvalService = approvalService;
        this.assembler = assembler;
    }

    @GetMapping
    public PageResponse<MatchSummaryResponse> list(
            @RequestParam(required = false) MatchStatus status,
            @RequestParam(required = false) UUID seasonId,
            Pageable pageable) {
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
                        e.getPayloadJson(), e.getCreatedAt()))
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchResponse create(@Valid @RequestBody CreateMatchRequest req) {
        UUID actor = CurrentAccount.require().accountId();
        Match match = matchService.create(req.seasonId(), req.drawMode(), req.playerIds(), actor);
        return assembler.toResponse(match);
    }

    @PostMapping("/{id}/draw")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public DrawResponse draw(@PathVariable UUID id) {
        UUID actor = CurrentAccount.require().accountId();
        DrawResult result = drawService.draw(id, actor);
        return toDrawResponse(id, result);
    }

    @PostMapping("/{id}/draw/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchResponse confirmDraw(@PathVariable UUID id) {
        UUID actor = CurrentAccount.require().accountId();
        return assembler.toResponse(drawService.confirm(id, actor));
    }

    @PostMapping("/{id}/results")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchResponse submitResults(@PathVariable UUID id, @Valid @RequestBody SubmitResultsRequest req) {
        UUID actor = CurrentAccount.require().accountId();
        return assembler.toResponse(resultService.saveResults(id, req, actor));
    }

    @PatchMapping("/{id}/results")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchResponse editResults(@PathVariable UUID id, @Valid @RequestBody SubmitResultsRequest req) {
        UUID actor = CurrentAccount.require().accountId();
        return assembler.toResponse(resultService.saveResults(id, req, actor));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest req) {
        UUID actor = CurrentAccount.require().accountId();
        return assembler.toResponse(approvalService.approve(id, req, actor));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        UUID actor = CurrentAccount.require().accountId();
        return assembler.toResponse(approvalService.reject(id, req, actor));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse cancel(@PathVariable UUID id) {
        UUID actor = CurrentAccount.require().accountId();
        return assembler.toResponse(matchService.cancel(id, actor));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse reopen(@PathVariable UUID id) {
        UUID actor = CurrentAccount.require().accountId();
        return assembler.toResponse(approvalService.reopen(id, actor));
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
