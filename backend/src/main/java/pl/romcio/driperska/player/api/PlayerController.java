package pl.romcio.driperska.player.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.security.CurrentAccount;
import pl.romcio.driperska.common.web.PageResponse;
import pl.romcio.driperska.player.api.PlayerDtos.*;
import pl.romcio.driperska.player.application.PlayerService;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {
    private final PlayerService service;
    public PlayerController(PlayerService service) { this.service = service; }

    @GetMapping
    public PageResponse<PlayerResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String search, Pageable pageable) {
        return PageResponse.of(service.list(active, role, search, pageable).map(PlayerResponse::from));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PLAYER')")
    public PlayerResponse me() {
        return PlayerResponse.from(service.getByAccountId(CurrentAccount.require().accountId()));
    }

    @GetMapping("/{id}")
    public PlayerResponse get(@PathVariable UUID id) { return PlayerResponse.from(service.get(id)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public PlayerResponse create(@Valid @RequestBody CreatePlayerRequest req) {
        return PlayerResponse.from(service.create(req));
    }

    @PostMapping("/with-account")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CreatedPlayerResponse createWithAccount(@Valid @RequestBody CreatePlayerRequest req) {
        return service.createWithAccount(req);
    }

    @PostMapping("/{id}/account")
    @PreAuthorize("hasRole('ADMIN')")
    public CreatedPlayerResponse provisionAccount(@PathVariable UUID id) {
        return service.provisionExisting(id);
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('PLAYER')")
    public PlayerResponse updateMe(@Valid @RequestBody SelfUpdatePlayerRequest req) {
        return PlayerResponse.from(service.updateSelf(CurrentAccount.require().accountId(), req));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public PlayerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePlayerRequest req) {
        return PlayerResponse.from(service.update(id, req));
    }

    @PostMapping(value = "/me/avatar", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('PLAYER')")
    public PlayerResponse uploadMyAvatar(@RequestParam("file") MultipartFile file) {
        return PlayerResponse.from(service.updateSelfAvatar(CurrentAccount.require().accountId(), file));
    }

    @PostMapping(value = "/{id}/avatar", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public PlayerResponse uploadAvatar(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return PlayerResponse.from(service.updateAvatar(id, file));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) { service.softDelete(id); }
}