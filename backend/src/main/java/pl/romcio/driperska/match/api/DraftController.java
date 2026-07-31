package pl.romcio.driperska.match.api;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.security.CurrentAccount;
import pl.romcio.driperska.match.application.DraftChatService;
import pl.romcio.driperska.match.application.DraftService;
import pl.romcio.driperska.match.application.DraftSetupService;
import pl.romcio.driperska.match.application.DrawLobbyService;
import pl.romcio.driperska.match.application.draft.DraftState.SwapType;

@RestController
@RequestMapping("/api/v1/draft")
public class DraftController {

    private final DraftService draftService;
    private final DraftSetupService setupService;
    private final DraftChatService chatService;
    private final DrawLobbyService lobbyService;

    public DraftController(DraftService draftService, DraftSetupService setupService,
                           DraftChatService chatService, DrawLobbyService lobbyService) {
        this.draftService = draftService;
        this.setupService = setupService;
        this.chatService = chatService;
        this.lobbyService = lobbyService;
    }

    // --- before the first ban: captain, pick order, readiness --------------------------------

    /** Vote a team-mate in as captain. Three of five (or everyone having voted) settles it. */
    @PostMapping("/{matchId}/captain-vote")
    @PreAuthorize("hasRole('PLAYER')")
    public void voteCaptain(@PathVariable UUID matchId, @RequestBody CaptainVoteRequest req) {
        setupService.voteCaptain(matchId, CurrentAccount.require().accountId(), req.playerId());
        lobbyService.publishUpdate(matchId);
    }

    /** The captain arranges who picks first, second, … in their team. */
    @PostMapping("/{matchId}/order")
    @PreAuthorize("hasRole('PLAYER')")
    public void setOrder(@PathVariable UUID matchId, @RequestBody OrderRequest req) {
        setupService.setOrder(matchId, CurrentAccount.require().accountId(), req.playerIds());
        lobbyService.publishUpdate(matchId);
    }

    /** The captain declares the team ready. Both teams ready and the draft starts by itself. */
    @PostMapping("/{matchId}/ready")
    @PreAuthorize("hasRole('PLAYER')")
    public void setReady(@PathVariable UUID matchId, @RequestBody ReadyRequest req) {
        setupService.setReady(matchId, CurrentAccount.require().accountId(), req.ready());
        lobbyService.publishUpdate(matchId);
    }

    @PostMapping("/{matchId}/setup/captain")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void adminSetCaptain(@PathVariable UUID matchId, @RequestBody AdminCaptainRequest req) {
        setupService.adminSetCaptain(matchId, req.side(), req.playerId(),
                CurrentAccount.require().accountId());
        lobbyService.publishUpdate(matchId);
    }

    @PostMapping("/{matchId}/setup/ready")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void adminSetReady(@PathVariable UUID matchId, @RequestBody AdminReadyRequest req) {
        setupService.adminSetReady(matchId, req.side(), req.ready(),
                CurrentAccount.require().accountId());
        lobbyService.publishUpdate(matchId);
    }

    @PostMapping("/{matchId}/setup/reset")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void adminResetSetup(@PathVariable UUID matchId) {
        setupService.adminReset(matchId);
        lobbyService.publishUpdate(matchId);
    }

    // --- chat (in memory only, delivered over the lobby stream) ------------------------------

    @PostMapping("/{matchId}/chat")
    @PreAuthorize("isAuthenticated()")
    public void chat(@PathVariable UUID matchId, @RequestBody ChatRequest req) {
        var account = CurrentAccount.require();
        if (isStaff(account)) {
            chatService.sendAsAdmin(matchId, account.accountId(), account.username(), req.text());
        } else {
            chatService.sendAsPlayer(matchId, account.accountId(),
                    req.scope() == null ? DraftChatService.Scope.ALL : req.scope(), req.text());
        }
    }

    /** Recent lines this viewer may see — the stream only carries what happens from now on. */
    @GetMapping("/{matchId}/chat")
    @PreAuthorize("isAuthenticated()")
    public List<DraftChatService.ChatMessage> chatHistory(@PathVariable UUID matchId) {
        var account = CurrentAccount.require();
        return chatService.history(matchId, account.accountId(), isStaff(account));
    }

    private static boolean isStaff(pl.romcio.driperska.common.security.AuthenticatedAccount account) {
        return account.isAdmin() || "ROLE_EDITOR".equals(account.role());
    }

    public record ChampionRequest(@NotNull Integer championId) {}
    /** Captain vote: a team-mate on your own side (yourself is allowed). */
    public record CaptainVoteRequest(@NotNull UUID playerId) {}
    /** Pick order set by the captain — exactly the five of their side, or empty for a shuffle. */
    public record OrderRequest(List<UUID> playerIds) {}
    public record ReadyRequest(boolean ready) {}
    public record AdminCaptainRequest(@NotNull Side side, @NotNull UUID playerId) {}
    public record AdminReadyRequest(@NotNull Side side, boolean ready) {}
    public record ChatRequest(DraftChatService.Scope scope, @NotNull String text) {}
    public record SwapRequest(@NotNull UUID targetPlayerId, @NotNull SwapType type) {}
    /** Null championId clears the pre-selection. */
    public record HoverRequest(Integer championId) {}
    /** Admin correction of one player's champion; null championId clears the slot. */
    public record AdminChampionRequest(@NotNull UUID playerId, Integer championId) {}

    @PostMapping("/{matchId}/ban")
    @PreAuthorize("hasRole('PLAYER')")
    public void ban(@PathVariable UUID matchId, @RequestBody ChampionRequest req) {
        draftService.ban(matchId, CurrentAccount.require().accountId(), req.championId());
        lobbyService.publishUpdate(matchId);
    }

    @PostMapping("/{matchId}/pick")
    @PreAuthorize("hasRole('PLAYER')")
    public void pick(@PathVariable UUID matchId, @RequestBody ChampionRequest req) {
        draftService.pick(matchId, CurrentAccount.require().accountId(), req.championId());
        lobbyService.publishUpdate(matchId);
    }

    /** Highlight (or clear) the pre-selection of the player on the clock; broadcast to both teams. */
    @PostMapping("/{matchId}/hover")
    @PreAuthorize("hasRole('PLAYER')")
    public void hover(@PathVariable UUID matchId, @RequestBody HoverRequest req) {
        draftService.hover(matchId, CurrentAccount.require().accountId(), req.championId());
        lobbyService.publishUpdate(matchId);
    }

    /** Admin fixes a player's champion (e.g. they locked the wrong one) without touching turn order. */
    @PostMapping("/{matchId}/champion")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void setChampion(@PathVariable UUID matchId, @RequestBody AdminChampionRequest req) {
        draftService.adminSetChampion(matchId, req.playerId(), req.championId(),
                CurrentAccount.require().accountId());
        lobbyService.publishUpdate(matchId);
    }

    @PostMapping("/{matchId}/swap")
    @PreAuthorize("hasRole('PLAYER')")
    public void requestSwap(@PathVariable UUID matchId, @RequestBody SwapRequest req) {
        draftService.swapRequest(matchId, CurrentAccount.require().accountId(), req.targetPlayerId(), req.type());
        lobbyService.publishUpdate(matchId);
    }

    @PostMapping("/{matchId}/swap/{swapId}/accept")
    @PreAuthorize("hasRole('PLAYER')")
    public void acceptSwap(@PathVariable UUID matchId, @PathVariable UUID swapId) {
        draftService.swapAccept(matchId, CurrentAccount.require().accountId(), swapId);
        lobbyService.publishUpdate(matchId);
    }

    @PostMapping("/{matchId}/swap/{swapId}/cancel")
    @PreAuthorize("hasRole('PLAYER')")
    public void cancelSwap(@PathVariable UUID matchId, @PathVariable UUID swapId) {
        draftService.swapCancel(matchId, CurrentAccount.require().accountId(), swapId);
        lobbyService.publishUpdate(matchId);
    }

    @PostMapping("/{matchId}/start")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void start(@PathVariable UUID matchId) {
        draftService.startDraft(matchId, CurrentAccount.require().accountId());
        lobbyService.publishUpdate(matchId);
    }

    @PostMapping("/{matchId}/pause")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void pause(@PathVariable UUID matchId) {
        draftService.pause(matchId);
        lobbyService.publishUpdate(matchId);
    }

    @PostMapping("/{matchId}/resume")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void resume(@PathVariable UUID matchId) {
        draftService.resume(matchId);
        lobbyService.publishUpdate(matchId);
    }

    @PostMapping("/{matchId}/reset")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void reset(@PathVariable UUID matchId) {
        draftService.reset(matchId, CurrentAccount.require().accountId());
        lobbyService.publishUpdate(matchId);
    }
}
