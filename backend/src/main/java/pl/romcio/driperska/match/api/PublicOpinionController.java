package pl.romcio.driperska.match.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.match.api.MatchFeedbackDtos.PublicOpinion;
import pl.romcio.driperska.match.application.MatchFeedbackService;

/**
 * The one piece of post-match feedback that is public: recent praise, for the ticker on the landing
 * page. Deliberately its own controller — {@link MatchFeedbackController} is gated to players, and
 * hanging an anonymous endpoint off it would mean carving an exception into that gate.
 *
 * <p>See {@link MatchFeedbackService#recentPraise} for why criticism stays inside the app.
 */
@RestController
@RequestMapping("/api/v1/opinions")
public class PublicOpinionController {

    private static final int DEFAULT_LIMIT = 12;

    private final MatchFeedbackService service;

    public PublicOpinionController(MatchFeedbackService service) {
        this.service = service;
    }

    @GetMapping("/recent")
    public List<PublicOpinion> recent(@RequestParam(required = false) Integer limit) {
        return service.recentPraise(limit == null ? DEFAULT_LIMIT : limit);
    }
}
