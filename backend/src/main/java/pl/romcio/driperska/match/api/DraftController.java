package pl.romcio.driperska.match.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.common.security.CurrentAccount;
import pl.romcio.driperska.match.application.DraftService;
import pl.romcio.driperska.match.application.DrawLobbyService;
import pl.romcio.driperska.match.application.draft.DraftState.SwapType;

@RestController
@RequestMapping("/api/v1/draft")
public class DraftController {

    private final DraftService draftService;
    private final DrawLobbyService lobbyService;

    public DraftController(DraftService draftService, DrawLobbyService lobbyService) {
        this.draftService = draftService;
        this.lobbyService = lobbyService;
    }

    public record ChampionRequest(@NotNull Integer championId) {}
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
