package pl.romcio.driperska.integration.riot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/riot/tournament")
public class RiotCallbackController {
    private final RiotCallbackService service;

    public RiotCallbackController(RiotCallbackService service) {
        this.service = service;
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(
            @Valid @RequestBody RiotCallbackService.RiotCallbackRequest request) {
        service.receive(request);
        return ResponseEntity.ok().build();
    }
}

