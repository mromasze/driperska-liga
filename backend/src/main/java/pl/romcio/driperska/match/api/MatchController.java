package pl.romcio.driperska.match.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.common.security.CurrentAccount;
import pl.romcio.driperska.common.web.PageResponse;
import pl.romcio.driperska.integration.riot.RiotLobbyDtos.LobbyStatusResponse;
import pl.romcio.driperska.integration.riot.RiotResultImportService;
import pl.romcio.driperska.integration.riot.TournamentMatchService;
import pl.romcio.driperska.match.api.MatchDtos.*;
import pl.romcio.driperska.match.application.*;
import pl.romcio.driperska.match.application.DrawService.DrawResult;
import pl.romcio.driperska.match.domain.DrawMode;
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
    private final TournamentMatchService tournamentMatchService;
    private final RiotResultImportService riotResultImportService;
    private final MatchReplayService replayService;
    private final MatchShareService shareService;
    private final MatchOcrService ocrService;

    public MatchController(MatchService matchService, DrawLobbyService drawLobbyService,
                           ResultService resultService, ApprovalService approvalService,
                           MatchAssembler assembler, TournamentMatchService tournamentMatchService,
                           RiotResultImportService riotResultImportService,
                           MatchReplayService replayService, MatchShareService shareService,
                           MatchOcrService ocrService) {
        this.matchService = matchService;
        this.drawLobbyService = drawLobbyService;
        this.resultService = resultService;
        this.approvalService = approvalService;
        this.assembler = assembler;
        this.tournamentMatchService = tournamentMatchService;
        this.riotResultImportService = riotResultImportService;
        this.replayService = replayService;
        this.shareService = shareService;
        this.ocrService = ocrService;
    }

    /**
     * Lists ordered by the actual game start (startedAt), never by creation/update time — edits
     * and approvals must not reshuffle the list. Matches not started yet fall back to createdAt.
     */
    private static final Sort DEFAULT_LIST_SORT = Sort.by(
            Sort.Order.desc("startedAt").with(Sort.NullHandling.NULLS_LAST),
            Sort.Order.desc("createdAt"));

    @GetMapping
    public PageResponse<MatchSummaryResponse> list(
            @RequestParam(required = false) MatchStatus status,
            @RequestParam(required = false) UUID seasonId,
            Pageable pageable) {
        Pageable effectivePageable = pageable.getSort().isSorted() ? pageable
                : org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_LIST_SORT);
        MatchStatus effective = CurrentAccount.optional().isPresent() ? status : MatchStatus.APPROVED;
        return PageResponse.of(matchService.list(effective, seasonId, effectivePageable)
                .map(assembler::toSummary));
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
        if (req.drawMode() == DrawMode.MANUAL) {
            drawLobbyService.adminManualDraw(match.getId(), actor, toManualSlots(req.teams()));
        } else {
            drawLobbyService.adminDraw(match.getId(), actor);
        }
        return assembler.toResponse(matchService.get(match.getId()));
    }

    @PostMapping("/{id}/draw")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public DrawResponse draw(@PathVariable UUID id) {
        UUID actor = CurrentAccount.require().accountId();
        return toDrawResponse(id, drawLobbyService.adminDraw(id, actor));
    }

    /** Re-assign teams by hand on an existing match (side + role per player). */
    @PostMapping("/{id}/draw/manual")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public DrawResponse drawManual(@PathVariable UUID id, @Valid @RequestBody ManualDrawRequest req) {
        UUID actor = CurrentAccount.require().accountId();
        return toDrawResponse(id, drawLobbyService.adminManualDraw(id, actor, toManualSlots(req.teams())));
    }

    private java.util.List<DrawService.ManualSlot> toManualSlots(java.util.List<ManualSlotRequest> teams) {
        if (teams == null) {
            throw new pl.romcio.driperska.common.error.BusinessRuleException(
                    "Tryb ręczny wymaga przypisania graczy do drużyn");
        }
        return teams.stream()
                .map(s -> new DrawService.ManualSlot(s.playerId(), s.side(), s.role()))
                .toList();
    }

    @PostMapping("/{id}/draw/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchResponse confirmDraw(@PathVariable UUID id) {
        return assembler.toResponse(drawLobbyService.adminConfirm(
                id, CurrentAccount.require().accountId()));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse start(@PathVariable UUID id) {
        Match match = tournamentMatchService.start(id, CurrentAccount.require().accountId());
        drawLobbyService.publishUpdate(id);
        return assembler.toResponse(match);
    }

    @PostMapping("/{id}/start/manual")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchResponse startManual(@PathVariable UUID id) {
        Match match = tournamentMatchService.startManual(id, CurrentAccount.require().accountId());
        drawLobbyService.publishUpdate(id);
        return assembler.toResponse(match);
    }

    /** Live draw/vote state for the admin panel (vote tally + teams). */
    @GetMapping("/{id}/draw-state")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public pl.romcio.driperska.match.api.DrawLobbyDtos.DrawLobbyResponse drawState(@PathVariable UUID id) {
        return drawLobbyService.stateForMatch(id);
    }

    @PostMapping("/{id}/players/replace")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse replacePlayer(@PathVariable UUID id,
                                       @Valid @RequestBody ReplacePlayerRequest req) {
        Match match = tournamentMatchService.replacePlayer(id, req.removedPlayerId(),
                req.addedPlayerId(), CurrentAccount.require().accountId());
        drawLobbyService.publishUpdate(id);
        return assembler.toResponse(match);
    }

    @GetMapping("/{id}/riot/lobby")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public LobbyStatusResponse riotLobby(@PathVariable UUID id) {
        return tournamentMatchService.lobbyStatus(id);
    }

    @PostMapping("/{id}/riot/import")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse importRiotResults(@PathVariable UUID id) {
        Match match = riotResultImportService.importNow(id);
        drawLobbyService.publishUpdate(id);
        return assembler.toResponse(match);
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

    @PostMapping(value = "/{id}/replay", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchResponse uploadReplay(@PathVariable UUID id,
                                      @org.springframework.web.bind.annotation.RequestParam("file")
                                      org.springframework.web.multipart.MultipartFile file) {
        return assembler.toResponse(replayService.store(id, file, CurrentAccount.require().accountId()));
    }

    /** Raw PNG preview of the result card (used by the share dialog). */
    @GetMapping(value = "/{id}/share/image", produces = org.springframework.http.MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public byte[] shareImage(@PathVariable UUID id) {
        return shareService.renderImage(id);
    }

    @PostMapping("/{id}/share/discord")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchShareService.ShareResult shareToDiscord(@PathVariable UUID id) {
        return shareService.shareToDiscord(id, CurrentAccount.require().accountId());
    }

    /** Reads LoL end-game screenshot(s) via Ollama vision and returns an editable results draft. */
    @PostMapping(value = "/{id}/results/ocr", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public MatchOcrService.OcrDraft ocrResults(@PathVariable UUID id,
                                               @org.springframework.web.bind.annotation.RequestParam("files")
                                               java.util.List<org.springframework.web.multipart.MultipartFile> files) {
        return ocrService.extract(id, files);
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