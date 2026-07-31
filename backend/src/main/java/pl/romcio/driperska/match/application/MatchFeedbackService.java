package pl.romcio.driperska.match.application;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.match.api.MatchFeedbackDtos.FeedbackParticipant;
import pl.romcio.driperska.match.api.MatchFeedbackDtos.MyFeedback;
import pl.romcio.driperska.match.api.MatchFeedbackDtos.RateableMatch;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchFeedback;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchFeedbackRepository;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

@Service
public class MatchFeedbackService {
    private static final int MAX_RATEABLE = 5;

    private final MatchRepository matchRepository;
    private final MatchFeedbackRepository feedbackRepository;
    private final PlayerRepository playerRepository;

    public MatchFeedbackService(MatchRepository matchRepository, MatchFeedbackRepository feedbackRepository,
                                PlayerRepository playerRepository) {
        this.matchRepository = matchRepository;
        this.feedbackRepository = feedbackRepository;
        this.playerRepository = playerRepository;
    }

    /** Recently finished matches the player was in, that they can still rate. */
    @Transactional(readOnly = true)
    public List<RateableMatch> rateable(UUID accountId) {
        Player voter = playerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessRuleException("Konto nie jest połączone z graczem"));
        List<Match> matches = matchRepository.findForPlayerAndStatuses(voter.getId(),
                EnumSet.of(MatchStatus.APPROVED), PageRequest.of(0, 20));
        List<RateableMatch> out = new ArrayList<>();
        for (Match match : matches) {
            MatchFeedback existing = feedbackRepository
                    .findByMatchIdAndVoterPlayerId(match.getId(), voter.getId()).orElse(null);
            out.add(new RateableMatch(match.getId(), match.getStartedAt(), match.getCompletedAt(),
                    participants(match),
                    existing == null ? null : new MyFeedback(existing.getUpvotePlayerId(),
                            existing.getDownvotePlayerId(), existing.getNote())));
            if (out.size() >= MAX_RATEABLE) break;
        }
        return out;
    }

    @Transactional
    public MyFeedback submit(UUID matchId, UUID accountId, UUID upvote, UUID downvote, String note) {
        Player voter = playerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessRuleException("Konto nie jest połączone z graczem"));
        Match match = matchRepository.findDetailedById(matchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Match", matchId));
        if (match.getStatus() != MatchStatus.APPROVED) {
            throw new BusinessRuleException("Ocenić można dopiero zakończony i zatwierdzony mecz");
        }
        List<UUID> playerIds = match.getPoolPlayerIds();
        if (!playerIds.contains(voter.getId())) {
            throw new BusinessRuleException("Tylko uczestnik meczu może go ocenić");
        }
        if (upvote != null && !playerIds.contains(upvote)) {
            throw new BusinessRuleException("Wyróżniony gracz nie brał udziału w tym meczu");
        }
        if (downvote != null && !playerIds.contains(downvote)) {
            throw new BusinessRuleException("Oceniany gracz nie brał udziału w tym meczu");
        }
        if (upvote != null && upvote.equals(voter.getId())) {
            throw new BusinessRuleException("Nie możesz wyróżnić samego siebie");
        }
        if (downvote != null && downvote.equals(voter.getId())) {
            throw new BusinessRuleException("Nie możesz ocenić negatywnie samego siebie");
        }
        if (upvote != null && upvote.equals(downvote)) {
            throw new BusinessRuleException("Nie możesz dać tej samej osobie plusa i minusa");
        }
        MatchFeedback feedback = feedbackRepository
                .findByMatchIdAndVoterPlayerId(matchId, voter.getId())
                .orElseGet(() -> new MatchFeedback(matchId, voter.getId()));
        String trimmed = note == null ? null : note.trim();
        feedback.update(upvote, downvote, trimmed == null || trimmed.isBlank() ? null : trimmed);
        feedbackRepository.save(feedback);
        return new MyFeedback(upvote, downvote, feedback.getNote());
    }

    /**
     * The public opinion ticker on the landing page.
     *
     * <p>Praise only, and that is a deliberate line. Inside the app a signed-in member sees both
     * tones on the match page, because knowing you were called out is part of the feedback loop.
     * The landing page is open to the internet, so anonymous criticism of a named person does not go
     * there — a public highlight reel of what people said well about each other does. Switching this
     * to include negatives means widening {@link MatchFeedbackRepository#findRecentPraise} and adding
     * the tone to the payload; the frontend needs no other change.
     */
    @Transactional(readOnly = true)
    public List<pl.romcio.driperska.match.api.MatchFeedbackDtos.PublicOpinion> recentPraise(int limit) {
        List<MatchFeedback> recent = feedbackRepository.findRecentPraise(
                PageRequest.of(0, Math.clamp(limit, 1, 30)));
        Map<UUID, Match> matches = new HashMap<>();
        matchRepository.findAllById(recent.stream().map(MatchFeedback::getMatchId).distinct().toList())
                .forEach(match -> matches.put(match.getId(), match));
        Map<UUID, Player> players = new HashMap<>();
        playerRepository.findByIdIn(recent.stream().map(MatchFeedback::getUpvotePlayerId).distinct().toList())
                .forEach(player -> players.put(player.getId(), player));

        List<pl.romcio.driperska.match.api.MatchFeedbackDtos.PublicOpinion> out = new ArrayList<>();
        for (MatchFeedback fb : recent) {
            Match match = matches.get(fb.getMatchId());
            Player about = players.get(fb.getUpvotePlayerId());
            // A reopened match is back under review, so its feedback stops being public until it is
            // signed off again.
            if (match == null || about == null || match.getStatus() != MatchStatus.APPROVED) continue;
            out.add(new pl.romcio.driperska.match.api.MatchFeedbackDtos.PublicOpinion(
                    fb.getMatchId(), match.getStartedAt() != null ? match.getStartedAt() : match.getCompletedAt(),
                    about.getId(), about.getNickname(), about.getAvatarUrl(),
                    fb.getNote().trim(), fb.getUpdatedAt()));
        }
        return out;
    }

    /** Aggregated peer feedback for a match: per-player up/down counts + anonymous comments. */
    @Transactional(readOnly = true)
    public pl.romcio.driperska.match.api.MatchFeedbackDtos.MatchFeedbackSummary summary(UUID matchId) {
        Match match = matchRepository.findDetailedById(matchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Match", matchId));
        Map<UUID, Player> byId = new HashMap<>();
        playerRepository.findByIdIn(match.getPoolPlayerIds()).forEach(p -> byId.put(p.getId(), p));
        Map<UUID, MatchParticipant> partById = new HashMap<>();
        for (MatchParticipant p : match.getParticipants()) partById.put(p.getPlayerId(), p);

        Map<UUID, int[]> counts = new HashMap<>(); // [upvotes, downvotes]
        Map<UUID, List<pl.romcio.driperska.match.api.MatchFeedbackDtos.FeedbackComment>> comments = new HashMap<>();
        List<MatchFeedback> feedbacks = feedbackRepository.findByMatchId(matchId);
        for (MatchFeedback fb : feedbacks) {
            String note = fb.getNote() == null ? null : fb.getNote().trim();
            if (fb.getUpvotePlayerId() != null) {
                counts.computeIfAbsent(fb.getUpvotePlayerId(), k -> new int[2])[0]++;
                if (note != null && !note.isBlank()) {
                    comments.computeIfAbsent(fb.getUpvotePlayerId(), k -> new ArrayList<>())
                            .add(new pl.romcio.driperska.match.api.MatchFeedbackDtos.FeedbackComment("POSITIVE", note));
                }
            }
            if (fb.getDownvotePlayerId() != null) {
                counts.computeIfAbsent(fb.getDownvotePlayerId(), k -> new int[2])[1]++;
                if (note != null && !note.isBlank()) {
                    comments.computeIfAbsent(fb.getDownvotePlayerId(), k -> new ArrayList<>())
                            .add(new pl.romcio.driperska.match.api.MatchFeedbackDtos.FeedbackComment("NEGATIVE", note));
                }
            }
        }

        List<pl.romcio.driperska.match.api.MatchFeedbackDtos.PlayerFeedbackSummary> players = new ArrayList<>();
        for (UUID playerId : counts.keySet()) {
            int[] c = counts.get(playerId);
            Player player = byId.get(playerId);
            MatchParticipant part = partById.get(playerId);
            players.add(new pl.romcio.driperska.match.api.MatchFeedbackDtos.PlayerFeedbackSummary(
                    playerId, player != null ? player.getNickname() : "?",
                    part != null ? part.getSide() : null, part != null ? part.getRole() : null,
                    c[0], c[1], comments.getOrDefault(playerId, List.of())));
        }
        players.sort((a, b) -> Integer.compare(b.upvotes() + b.downvotes(), a.upvotes() + a.downvotes()));
        return new pl.romcio.driperska.match.api.MatchFeedbackDtos.MatchFeedbackSummary(feedbacks.size(), players);
    }

    private List<FeedbackParticipant> participants(Match match) {
        Map<UUID, Player> byId = new HashMap<>();
        playerRepository.findByIdIn(match.getPoolPlayerIds()).forEach(p -> byId.put(p.getId(), p));
        List<FeedbackParticipant> list = new ArrayList<>();
        for (MatchParticipant p : match.getParticipants()) {
            Player player = byId.get(p.getPlayerId());
            list.add(new FeedbackParticipant(p.getPlayerId(),
                    player != null ? player.getNickname() : "?", p.getSide(), p.getRole()));
        }
        return list;
    }
}
