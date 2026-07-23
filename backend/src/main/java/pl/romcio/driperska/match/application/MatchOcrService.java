package pl.romcio.driperska.match.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.romcio.driperska.champion.domain.Champion;
import pl.romcio.driperska.champion.infra.ChampionRepository;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.integration.ollama.OllamaVisionClient;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

/**
 * Reads LoL end-game screenshots via an Ollama vision model and produces an editable results draft
 * (mapped to this match's players and champions). Nothing is saved — the admin reviews and submits.
 */
@Service
public class MatchOcrService {
    private static final long MAX_IMAGE_BYTES = 12L * 1024 * 1024;

    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final ChampionRepository championRepository;
    private final OllamaVisionClient ollama;

    public MatchOcrService(MatchRepository matchRepository, PlayerRepository playerRepository,
                           ChampionRepository championRepository, OllamaVisionClient ollama) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.championRepository = championRepository;
        this.ollama = ollama;
    }

    @Transactional(readOnly = true)
    public OcrDraft extract(UUID matchId, List<MultipartFile> images) {
        Match match = matchRepository.findDetailedById(matchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Match", matchId));
        if (images == null || images.isEmpty()) {
            throw new BusinessRuleException("Dodaj przynajmniej jeden screenshot podsumowania");
        }
        List<String> b64 = new ArrayList<>();
        for (MultipartFile file : images) {
            if (file.isEmpty()) continue;
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw new BusinessRuleException("Screenshot jest za duży (max 12 MB)");
            }
            String type = file.getContentType();
            if (type == null || !type.startsWith("image/")) {
                throw new BusinessRuleException("Dozwolone są tylko obrazy (PNG/JPG)");
            }
            try {
                // Downscale + re-encode so multiple/large screenshots stay well under Ollama's
                // request-body limit (a raw 12 MB PNG base64s to ~16 MB; several would 400).
                b64.add(downscaleToBase64(file.getBytes()));
            } catch (IOException ex) {
                throw new BusinessRuleException("Nie udało się odczytać pliku: " + file.getOriginalFilename());
            }
        }
        if (b64.isEmpty()) {
            throw new BusinessRuleException("Puste pliki obrazów");
        }

        JsonNode result = ollama.chatJson(prompt(), b64, schema());
        return toDraft(match, result);
    }

    private OcrDraft toDraft(Match match, JsonNode result) {
        List<Player> players = playerRepository.findByIdIn(match.getPoolPlayerIds());
        // name -> playerId (by riot game name and by nickname)
        Map<String, UUID> byName = new HashMap<>();
        for (Player p : players) {
            if (p.getNickname() != null) byName.putIfAbsent(norm(p.getNickname()), p.getId());
            String gameName = riotGameName(p.getRiotId());
            if (gameName != null) byName.putIfAbsent(norm(gameName), p.getId());
        }
        Map<UUID, Side> sideByPlayer = new HashMap<>();
        Map<UUID, String> nickById = new HashMap<>();
        for (MatchParticipant mp : match.getParticipants()) sideByPlayer.put(mp.getPlayerId(), mp.getSide());
        for (Player p : players) nickById.put(p.getId(), p.getNickname());

        Map<String, Integer> champByName = new HashMap<>();
        for (Champion c : championRepository.findAll()) champByName.putIfAbsent(norm(c.getName()), c.getId());

        List<OcrRow> rows = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        Map<Side, Integer> winsBySide = new HashMap<>();
        java.util.Set<UUID> used = new java.util.HashSet<>();

        for (JsonNode pl : result.path("players")) {
            String name = pl.path("name").asText("");
            UUID playerId = resolve(byName, name, used);
            if (playerId == null) {
                if (!name.isBlank()) unmatched.add(name);
                continue;
            }
            used.add(playerId);
            String champName = pl.path("champion").asText(null);
            Integer champId = champName == null ? null : champByName.get(norm(champName));
            rows.add(new OcrRow(playerId, nickById.get(playerId), mapRole(pl.path("role").asText(null)),
                    champId, champName,
                    pl.path("kills").asInt(0), pl.path("deaths").asInt(0), pl.path("assists").asInt(0),
                    pl.path("cs").asInt(0), pl.path("gold").asInt(0), pl.path("damage").asInt(0),
                    pl.path("vision").asInt(0), Math.max(1, pl.path("largestMultiKill").asInt(1))));
            if (pl.path("win").asBoolean(false)) {
                Side s = sideByPlayer.get(playerId);
                if (s != null) winsBySide.merge(s, 1, Integer::sum);
            }
        }

        String winningSide = result.path("winningSide").asText("UNKNOWN");
        if (!"BLUE".equals(winningSide) && !"RED".equals(winningSide)) {
            winningSide = winsBySide.getOrDefault(Side.BLUE, 0) > winsBySide.getOrDefault(Side.RED, 0) ? "BLUE"
                    : winsBySide.getOrDefault(Side.RED, 0) > 0 ? "RED" : null;
        }
        Integer duration = result.path("durationSeconds").isInt() ? result.path("durationSeconds").asInt() : null;

        List<String> missing = new ArrayList<>();
        for (Player p : players) {
            if (rows.stream().noneMatch(r -> r.playerId().equals(p.getId()))) missing.add(p.getNickname());
        }
        return new OcrDraft(winningSide, duration, rows, unmatched, missing);
    }

    private static UUID resolve(Map<String, UUID> byName, String rawName, java.util.Set<UUID> used) {
        String n = norm(rawName);
        if (n.isBlank()) return null;
        UUID exact = byName.get(n);
        if (exact != null && !used.contains(exact)) return exact;
        // contains-match fallback (in-game name vs nickname differences)
        for (Map.Entry<String, UUID> e : byName.entrySet()) {
            if (used.contains(e.getValue())) continue;
            if (e.getKey().contains(n) || n.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    private static String riotGameName(String riotId) {
        if (riotId == null) return null;
        int hash = riotId.lastIndexOf('#');
        return hash > 0 ? riotId.substring(0, hash) : riotId;
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Longest edge for images sent to the vision model — plenty to read a scoreboard, tiny payload. */
    private static final int MAX_DIM = 1600;

    /** Resize to {@code MAX_DIM} and re-encode as JPEG (q≈0.85); falls back to raw bytes if unreadable. */
    static String downscaleToBase64(byte[] data) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
        if (src == null) {
            return Base64.getEncoder().encodeToString(data); // unknown format — send as-is
        }
        int w = src.getWidth(), h = src.getHeight();
        double scale = Math.min(1.0, (double) MAX_DIM / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage rgb = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB); // flatten alpha for JPEG
        Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!writeJpeg(rgb, bos)) {
            ImageIO.write(rgb, "jpg", bos);
        }
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    private static boolean writeJpeg(BufferedImage img, ByteArrayOutputStream bos) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) return false;
        javax.imageio.ImageWriter writer = writers.next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return true;
    }

    private static String prompt() {
        return """
                Jesteś ekspertem od czytania ekranów podsumowania meczu League of Legends.
                Na obrazkach jest tablica wyników po meczu (może być kilka zrzutów: różne zakładki
                jak obrażenia, złoto, wizja — połącz dane tego samego gracza po nazwie przywoływacza).
                Wyodrębnij WSZYSTKICH graczy (zwykle 10). Dla każdego podaj:
                - name: nazwa przywoływacza / Riot ID pokazana przy graczu (bez #TAG jeśli jest),
                - champion: nazwa bohatera,
                - role: pozycja na jakiej grał (TOP/JUNGLE/MID/BOT/SUPPORT) — jeśli widać po ikonie
                  pozycji lub można wywnioskować z bohatera/lane; jeśli nie wiadomo, pomiń,
                - team: BLUE (niebiescy) lub RED (czerwoni),
                - win: true jeśli jego drużyna wygrała,
                - kills, deaths, assists: z kolumny KDA (K/D/A),
                - cs: creep score (liczba zabitych stworów/minionów),
                - gold: zdobyte złoto (jeśli widoczne, inaczej 0),
                - damage: obrażenia zadane bohaterom (jeśli widoczne, inaczej 0),
                - vision: vision score (jeśli widoczny, inaczej 0),
                - largestMultiKill: największy multikill (1 jeśli nieznany).
                Podaj też winningSide (BLUE/RED) i durationSeconds jeśli widać czas gry.
                Zwróć wyłącznie dane zgodne ze schematem. Nie zgaduj wartości, których nie ma — daj 0.
                """;
    }

    private static Map<String, Object> schema() {
        Map<String, Object> intType = Map.of("type", "integer");
        Map<String, Object> playerProps = obj(
                "name", Map.of("type", "string"),
                "champion", Map.of("type", "string"),
                "role", Map.of("type", "string", "enum", List.of("TOP", "JUNGLE", "MID", "BOT", "SUPPORT")),
                "team", Map.of("type", "string", "enum", List.of("BLUE", "RED")),
                "win", Map.of("type", "boolean"),
                "kills", intType, "deaths", intType, "assists", intType, "cs", intType,
                "gold", intType, "damage", intType, "vision", intType, "largestMultiKill", intType);
        Map<String, Object> player = obj(
                "type", "object",
                "properties", playerProps,
                "required", List.of("name", "kills", "deaths", "assists", "cs"));
        return obj(
                "type", "object",
                "properties", obj(
                        "winningSide", Map.of("type", "string", "enum", List.of("BLUE", "RED", "UNKNOWN")),
                        "durationSeconds", intType,
                        "players", obj("type", "array", "items", player)),
                "required", List.of("players"));
    }

    private static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static String mapRole(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "TOP" -> "TOP";
            case "JUNGLE", "JG", "JUNG" -> "JUNGLE";
            case "MID", "MIDDLE" -> "MID";
            case "BOT", "BOTTOM", "ADC", "AD", "CARRY" -> "ADC";
            case "SUPPORT", "SUP", "SUPP", "UTILITY" -> "SUPPORT";
            default -> null;
        };
    }

    public record OcrRow(UUID playerId, String nickname, String role, Integer championId, String championName,
                         int kills, int deaths, int assists, int cs, int gold,
                         int damageToChampions, int visionScore, int largestMultiKill) {}

    public record OcrDraft(String winningSide, Integer durationSeconds, List<OcrRow> rows,
                           List<String> unmatched, List<String> missing) {}
}
