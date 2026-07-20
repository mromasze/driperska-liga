package pl.romcio.driperska.player.api;

import jakarta.validation.Valid;
import java.util.UUID;
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
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.web.PageResponse;
import pl.romcio.driperska.player.api.PlayerDtos.CreatePlayerRequest;
import pl.romcio.driperska.player.api.PlayerDtos.PlayerResponse;
import pl.romcio.driperska.player.api.PlayerDtos.UpdatePlayerRequest;
import pl.romcio.driperska.player.application.PlayerService;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService service;

    public PlayerController(PlayerService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PlayerResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return PageResponse.of(service.list(active, role, search, pageable).map(PlayerResponse::from));
    }

    @GetMapping("/{id}")
    public PlayerResponse get(@PathVariable UUID id) {
        return PlayerResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public PlayerResponse create(@Valid @RequestBody CreatePlayerRequest req) {
        return PlayerResponse.from(service.create(req));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public PlayerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePlayerRequest req) {
        return PlayerResponse.from(service.update(id, req));
    }

    @PostMapping(value = "/{id}/avatar", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public PlayerResponse uploadAvatar(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return PlayerResponse.from(service.updateAvatar(id, file));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.softDelete(id);
    }
}
