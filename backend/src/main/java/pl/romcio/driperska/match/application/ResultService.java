package pl.romcio.driperska.match.application;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.champion.infra.ChampionRepository;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.match.api.MatchDtos.ParticipantResultInput;
import pl.romcio.driperska.match.api.MatchDtos.SubmitResultsRequest;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchApproval;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchApprovalRepository;

/** Records and edits match statistics, moving the match into the approval queue. */
@Service
public class ResultService {

    private final MatchService matchService;
    private final MatchApprovalRepository approvalRepository;
    private final ChampionRepository championRepository;
    private final MatchEventRecorder eventRecorder;

    public ResultService(MatchService matchService,
                         MatchApprovalRepository approvalRepository,
                         ChampionRepository championRepository,
                         MatchEventRecorder eventRecorder) {
        this.matchService = matchService;
        this.approvalRepository = approvalRepository;
        this.championRepository = championRepository;
        this.eventRecorder = eventRecorder;
    }

    @Transactional
    public Match saveResults(UUID matchId, SubmitResultsRequest req, UUID actor) {
        Match match = matchService.get(matchId);
        MatchStatus status = match.getStatus();
        if (status != MatchStatus.LIVE && status != MatchStatus.RESULTS_SUBMITTED
                && status != MatchStatus.REJECTED) {
            throw new BusinessRuleException("Wyniki można wpisywać dla meczu w toku lub odesłanego do edycji");
        }

        Map<UUID, MatchParticipant> byPlayer = new HashMap<>();
        for (MatchParticipant p : match.getParticipants()) {
            byPlayer.put(p.getPlayerId(), p);
        }
        if (req.participants().size() != byPlayer.size()) {
            throw new BusinessRuleException("Liczba wyników nie odpowiada składowi meczu");
        }

        for (ParticipantResultInput input : req.participants()) {
            MatchParticipant participant = byPlayer.get(input.playerId());
            if (participant == null) {
                throw new BusinessRuleException("Gracz spoza składu meczu: " + input.playerId());
            }
            if (!championRepository.existsById(input.championId())) {
                throw new BusinessRuleException("Nieznany champion: " + input.championId());
            }
            participant.setRole(input.role());
            participant.applyStats(input.championId(), input.kills(), input.deaths(), input.assists(),
                    input.cs(), input.gold(), input.damageToChampions(), input.visionScore(),
                    input.largestMultiKill());
        }

        long blue = match.getParticipants().stream().filter(p -> p.getSide() == pl.romcio.driperska.common.domain.Side.BLUE).count();
        long red = match.getParticipants().size() - blue;
        if (blue != 5 || red != 5) {
            throw new BusinessRuleException("Mecz musi mieć układ 5 vs 5");
        }

        match.setWinningSide(req.winningSide());
        match.setDurationSeconds(req.durationSeconds());
        match.setPatch(req.patch());

        boolean firstSubmission = status != MatchStatus.RESULTS_SUBMITTED;
        if (firstSubmission) {
            match.transitionTo(MatchStatus.RESULTS_SUBMITTED);
        }

        MatchApproval approval = approvalRepository.findByMatchId(matchId).orElse(null);
        if (approval == null) {
            approvalRepository.save(new MatchApproval(matchId, actor));
        } else {
            approval.resubmit(actor);
        }

        eventRecorder.record(matchId,
                firstSubmission ? MatchEventType.RESULTS_SUBMITTED : MatchEventType.RESULTS_EDITED,
                actor, Map.of("winningSide", req.winningSide().name()));
        return match;
    }
}
