package pl.romcio.driperska.match.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.romcio.driperska.common.security.CurrentAccount;
import pl.romcio.driperska.match.api.DrawLobbyDtos.DrawLobbyResponse;
import pl.romcio.driperska.match.api.DrawLobbyDtos.VoteRequest;
import pl.romcio.driperska.match.application.DrawLobbyService;
import pl.romcio.driperska.match.application.DrawRealtimeService;

@RestController
@RequestMapping("/api/v1/draw-lobby")
@PreAuthorize("hasRole('PLAYER')")
public class DrawLobbyController {
    private final DrawLobbyService service;
    private final DrawRealtimeService realtime;

    public DrawLobbyController(DrawLobbyService service, DrawRealtimeService realtime) {
        this.service = service;
        this.realtime = realtime;
    }

    @GetMapping("/active")
    public ResponseEntity<DrawLobbyResponse> active() {
        DrawLobbyResponse state = service.active(CurrentAccount.require().accountId());
        return state == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(state);
    }

    @PostMapping("/vote")
    public DrawLobbyResponse vote(@Valid @RequestBody VoteRequest request) {
        return service.vote(request.matchId(), request.decision(), CurrentAccount.require().accountId());
    }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream() {
        return realtime.subscribe(CurrentAccount.require().accountId());
    }
}