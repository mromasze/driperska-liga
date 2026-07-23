package pl.romcio.driperska.match.application;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.romcio.driperska.match.api.DrawLobbyDtos.DrawLobbyResponse;

@Service
public class DrawRealtimeService {
    private static final long TIMEOUT_MS = 30L * 60L * 1000L;
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID accountId) {
        return subscribe(accountId, null);
    }

    /**
     * Subscribes and, when {@code initialState} is present, immediately pushes it so a client that
     * refreshed or logged in mid-vote sees the current lobby without waiting for the next broadcast.
     */
    public SseEmitter subscribe(UUID accountId, DrawLobbyResponse initialState) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(accountId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable cleanup = () -> remove(accountId, emitter);
        emitter.onCompletion(cleanup);
        // Completing the emitter on timeout is required — otherwise Spring MVC raises
        // AsyncRequestTimeoutException on the async dispatch and logs a full stack trace.
        emitter.onTimeout(() -> { cleanup.run(); emitter.complete(); });
        emitter.onError(ignored -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"ok\":true}"));
            if (initialState != null) {
                emitter.send(SseEmitter.event().name("draw-state").data(initialState));
            }
        } catch (IOException | IllegalStateException ex) {
            cleanup.run();
        }
        return emitter;
    }

    public void broadcast(Collection<UUID> accountIds, DrawLobbyResponse state) {
        accountIds.stream().filter(java.util.Objects::nonNull).distinct()
                .forEach(accountId -> sendState(accountId, state));
    }

    /** Keeps Cloudflare and nginx from treating an idle draw stream as dead. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((accountId, listeners) -> listeners.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat " + Instant.now()));
            } catch (IOException | IllegalStateException ex) {
                remove(accountId, emitter);
            }
        }));
    }

    private void sendState(UUID accountId, DrawLobbyResponse state) {
        List<SseEmitter> listeners = emitters.getOrDefault(accountId, new CopyOnWriteArrayList<>());
        listeners.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("draw-state").data(state));
            } catch (IOException | IllegalStateException ex) {
                remove(accountId, emitter);
            }
        });
    }

    private void remove(UUID accountId, SseEmitter emitter) {
        List<SseEmitter> listeners = emitters.get(accountId);
        if (listeners == null) return;
        listeners.remove(emitter);
        if (listeners.isEmpty()) emitters.remove(accountId, listeners);
    }
}