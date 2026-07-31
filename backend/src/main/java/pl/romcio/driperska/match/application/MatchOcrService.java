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
import pl.romcio.driperska.champion.application.ChampionNameResolver;
import pl.romcio.driperska.champion.domain.Champion;
import pl.romcio.driperska.champion.infra.ChampionRepository;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.integration.ollama.OllamaVisionClient;
import pl.romcio.driperska.integration.ollama.OllamaVisionClient.ChatResult;
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
    private final ChampionReferenceAtlasService championAtlas;
    private final OllamaVisionClient ollama;
    private final pl.romcio.driperska.integration.ollama.OllamaProperties ollamaProperties;

    public MatchOcrService(MatchRepository matchRepository, PlayerRepository playerRepository,
                           ChampionRepository championRepository, ChampionReferenceAtlasService championAtlas,
                           OllamaVisionClient ollama,
                           pl.romcio.driperska.integration.ollama.OllamaProperties ollamaProperties) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.championRepository = championRepository;
        this.championAtlas = championAtlas;
        this.ollama = ollama;
        this.ollamaProperties = ollamaProperties;
    }

    @Transactional(readOnly = true)
    public OcrDraft extract(UUID matchId, List<MultipartFile> images) {
        Match match = matchRepository.findDetailedById(matchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Match", matchId));
        if (images == null || images.isEmpty()) {
            throw new BusinessRuleException("Dodaj przynajmniej jeden screenshot podsumowania");
        }

        List<OcrLogEntry> trace = new ArrayList<>();
        List<String> screenshots = new ArrayList<>();
        long originalBytes = 0;
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
                originalBytes += file.getSize();
                // Downscale + re-encode so multiple/large screenshots stay well under Ollama's
                // request-body limit (a raw 12 MB PNG base64s to ~16 MB; several would 400).
                screenshots.add(downscaleToBase64(file.getBytes()));
            } catch (IOException ex) {
                throw new BusinessRuleException("Nie udało się odczytać pliku: " + file.getOriginalFilename());
            }
        }
        if (screenshots.isEmpty()) {
            throw new BusinessRuleException("Puste pliki obrazów");
        }

        long preparedBytes = screenshots.stream().mapToLong(s -> s.length() * 3L / 4L).sum();
        trace.add(new OcrLogEntry("INPUT", "Załadowano " + screenshots.size() + " screenshot(y)."));
        trace.add(new OcrLogEntry("PREPROCESS", "Obrazy przygotowane dla modelu: "
                + megabytes(originalBytes) + " -> " + megabytes(preparedBytes) + "."));

        List<Player> players = playerRepository.findByIdIn(match.getPoolPlayerIds());
        List<Champion> champions = championRepository.findAllByOrderByNameAsc();
        ChampionReferenceAtlasService.Atlas atlas = ollamaProperties.isAtlasEnabled()
                ? championAtlas.atlasFor(champions)
                : ChampionReferenceAtlasService.Atlas.empty();
        List<String> modelImages = new ArrayList<>(screenshots);
        modelImages.addAll(atlas.images());
        boolean drafted = match.getParticipants().stream().allMatch(p -> p.getChampionId() != null)
                && !match.getParticipants().isEmpty();
        if (atlas.images().isEmpty()) {
            // Worth flagging loudly: without the atlas a manually recorded match has nothing to
            // identify champions from, and the column will come back empty.
            trace.add(new OcrLogEntry("CONTEXT", drafted
                    ? "Atlas portretów niedostępny, ale postacie są znane z draftu."
                    : "Atlas portretów niedostępny (Data Dragon?) — bez niego model nie rozpozna "
                        + "postaci w meczu wpisywanym ręcznie."));
        } else {
            String completeness = atlas.portraitCount() >= champions.size()
                    ? "pełny"
                    : "NIEPEŁNY — brakuje " + (champions.size() - atlas.portraitCount());
            trace.add(new OcrLogEntry("CONTEXT", "Dołączono " + atlas.images().size()
                    + " atlas(y) z " + atlas.portraitCount() + "/" + champions.size()
                    + " portretami championów (" + completeness + ", " + atlas.version() + ")."));
        }
        trace.add(new OcrLogEntry("CHAMPIONS", drafted
                ? "Mecz przeszedł przez draft — postacie znane, model ich nie zgaduje."
                : "Mecz wpisywany ręcznie — postacie rozpoznawane przez porównanie portretów z atlasem."));

        trace.add(new OcrLogEntry("REQUEST", "Wysyłam " + modelImages.size() + " obraz(y) do modelu "
                + "(screenshoty: " + screenshots.size() + ", referencje: " + atlas.images().size() + ")."));
        ChatResult chat = ollama.chatJson(systemPrompt(),
                prompt(match, players, champions, screenshots.size(), atlas.images().size()),
                modelImages, schema());
        trace.add(new OcrLogEntry("RESPONSE", "Model " + chat.model() + " odpowiedział po "
                + String.format(Locale.ROOT, "%.2f s", chat.elapsedMillis() / 1000.0) + "."));
        trace.add(new OcrLogEntry("MODEL", traceContent(chat.rawContent())));
        return toDraft(match, chat.data(), players, champions, trace);
    }

    private OcrDraft toDraft(Match match, JsonNode result, List<Player> players,
                             List<Champion> champions, List<OcrLogEntry> trace) {
        // name -> playerId (by riot game name and by nickname)
        Map<String, UUID> byName = new HashMap<>();
        for (Player p : players) {
            if (p.getNickname() != null) byName.putIfAbsent(norm(p.getNickname()), p.getId());
            String gameName = riotGameName(p.getRiotId());
            if (gameName != null) byName.putIfAbsent(norm(gameName), p.getId());
        }
        Map<UUID, Side> sideByPlayer = new HashMap<>();
        Map<UUID, String> nickById = new HashMap<>();
        Map<UUID, Integer> draftedChampionByPlayer = new HashMap<>();
        for (MatchParticipant mp : match.getParticipants()) {
            sideByPlayer.put(mp.getPlayerId(), mp.getSide());
            if (mp.getChampionId() != null) draftedChampionByPlayer.put(mp.getPlayerId(), mp.getChampionId());
        }
        for (Player p : players) nickById.put(p.getId(), p.getNickname());

        // Fuzzy on purpose: the model reads a 32px portrait and often writes a shorthand ("Nunu") or
        // slips a character. An exact-match-only lookup dropped all of those on the floor.
        ChampionNameResolver championResolver = ChampionNameResolver.of(champions);

        List<OcrRow> rows = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        List<String> unmatchedChampions = new ArrayList<>();
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
            Integer champId = championResolver.resolve(champName);
            if (champId == null && champName != null && !champName.isBlank()) {
                unmatchedChampions.add(champName.strip());
            }
            // The draft is authoritative: if it locked a champion for this player, the model reading a
            // portrait cannot beat it. This is also the safety net when the model reads nothing at all.
            Integer drafted = draftedChampionByPlayer.get(playerId);
            if (drafted != null) {
                champId = drafted;
            }
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
        long withChampion = rows.stream().filter(r -> r.championId() != null).count();
        trace.add(new OcrLogEntry("RESULT", "Rozpoznano i dopasowano " + rows.size() + "/"
                + players.size() + " graczy; postacie: " + withChampion + "/" + rows.size() + "."));
        if (!unmatchedChampions.isEmpty()) {
            // Worth saying out loud: it tells the admin whether the model failed to *read* the portrait
            // or merely wrote a name this backend could not map.
            trace.add(new OcrLogEntry("CHAMPIONS",
                    "Model podał postacie, których nie udało się dopasować: "
                            + String.join(", ", unmatchedChampions) + "."));
        }
        return new OcrDraft(winningSide, duration, rows, unmatched, missing,
                List.copyOf(unmatchedChampions), List.copyOf(trace));
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

    /** Only shrink genuinely huge images; otherwise keep full detail so tiny champion portraits stay
     *  readable. Longest edge cap + raw-byte threshold below which the screenshot is sent untouched. */
    private static final int MAX_DIM = 2560;
    private static final int PASSTHROUGH_BYTES = 4 * 1024 * 1024; // 4 MB — normal screenshots (~0.5–2 MB)

    /**
     * Base64-encode a screenshot for the vision model. Small/normal screenshots are sent AS-IS (no
     * re-encode, no downscale) to preserve champion-portrait sharpness — recompression blurs the tiny
     * icons and hurts recognition. Only oversized images (huge dimensions or many MB) are downscaled
     * to keep the request under Ollama's body limit.
     */
    static String downscaleToBase64(byte[] data) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
        if (src == null) {
            return Base64.getEncoder().encodeToString(data); // unknown format — send as-is
        }
        int w = src.getWidth(), h = src.getHeight();
        if (data.length <= PASSTHROUGH_BYTES && Math.max(w, h) <= MAX_DIM) {
            return Base64.getEncoder().encodeToString(data); // already small — keep full quality
        }
        double scale = Math.min(1.0, (double) MAX_DIM / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage rgb = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB); // flatten alpha for JPEG
        Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
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
        param.setCompressionQuality(0.92f);
        try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return true;
    }

    static String systemPrompt() {
        return """
                You are a deterministic OCR engine for League of Legends post-game scoreboards.
                Your only task is to convert the supplied match screenshots into the requested JSON schema.

                IMAGE RULES:
                - The user message states exactly which first images are MATCH SCREENSHOTS and which final
                  images are CHAMPION REFERENCE ATLASES.
                - Reference atlases contain labelled Data Dragon portraits. Use them only to identify the
                  small champion portrait in a player row. Never treat atlas labels, portraits, or rows as
                  match participants or match statistics.
                - Read every match screenshot. Multiple screenshots can show different statistic tabs for
                  the same ten players. Merge rows using summoner name, team, and stable row order.

                PLAYER IDENTITY — TWO NAMES PER PERSON (read this before naming anybody):
                - Every player in this league has TWO names: a LEAGUE NICKNAME used on the website, and
                  an IN-GAME NAME (the Riot ID without its #TAG) shown on the scoreboard. They are often
                  completely different strings, e.g. league nickname "Driper" playing as "xXSmurfik99Xx".
                - The user message lists the roster as: SIDE — league nickname "N" | in-game name "G".
                  That pairing is the correlation between what you see in the screenshot and what this
                  system stores. Learn it before you write any name.
                - In the "name" field return the LEAGUE NICKNAME of the person whose row you are reading,
                  matched through their in-game name. This is the only way the result can be attached to
                  the right player.
                - Only when a scoreboard row matches no roster entry, return the in-game name exactly as
                  printed, so the mismatch stays visible instead of being assigned to the wrong person.
                - Never pair two rows with the same league nickname, and never rename a player because
                  their champion or statistics look like somebody else's.
                - A roster entry marked "in-game name unknown" has no Riot ID on file: identify that
                  person by side and by the remaining rows, or leave their row out.

                EXTRACTION RULES:
                - Return exactly one player object for each player visible in the match screenshots.
                - Read summoner names from the screenshot; remove a trailing #TAG only when clearly
                  visible, then map the name onto the roster as described above.

                CHAMPION IDENTIFICATION (the scoreboard never writes champion names — it only shows a
                small portrait, so this is a visual comparison and you must actually perform it):
                - For each player row, look at that row's champion portrait.
                - Find the portrait in the reference atlases that depicts the same champion. Compare the
                  whole picture: face and skin colour, hair, helmet or horns, weapon, armour colour, and
                  the background tint. Portraits in a row of the scoreboard are cropped and small; the
                  atlas portrait is the same artwork at higher resolution.
                - Return the canonical champion name printed under the matching atlas portrait, spelled
                  exactly as printed, including punctuation and spaces.
                - Atlas sheets are alphabetical, continuing across sheets, so a name you half-recognise
                  can be confirmed by looking near its expected position.
                - Give your single best visual match for every row. Only omit champion when the portrait
                  is missing or unreadable — a best guess is more useful here than a blank.
                - Never derive the champion from the summoner name, the role, or the statistics.
                - K/D/A, CS, gold, champion damage, vision score, and largest multikill are different fields.
                  Use labels and tab context; never shift a value into the neighbouring column.
                - Determine team and winning side from BLUE/RED layout plus Victory/Defeat indicators.
                - Use the supplied expected roster only to correct OCR spelling and map sides. Do not invent
                  a participant who is absent from the screenshots.
                - Never guess a numeric value that is not visible. Use 0. Use largestMultiKill=1 if unknown.
                - If role is uncertain, omit it. Do not infer role only from the champion.
                - Output raw JSON only: no markdown fences, explanations, comments, or extra keys.
                """;
    }

    static String prompt(Match match, List<Player> players, List<Champion> champions,
                         int screenshotCount, int atlasCount) {
        Map<UUID, Player> playerById = new HashMap<>();
        for (Player player : players) playerById.put(player.getId(), player);
        Map<Integer, String> championNameById = new HashMap<>();
        for (Champion champion : champions) championNameById.put(champion.getId(), champion.getName());

        StringBuilder roster = new StringBuilder();
        List<String> draftedNames = new ArrayList<>();
        int participantCount = 0;
        for (MatchParticipant participant : match.getParticipants()) {
            Player player = playerById.get(participant.getPlayerId());
            if (player == null) continue;
            participantCount++;
            // Both names, always, and spelled out as a pair. Listing the in-game name only when it
            // differs left the model to guess whether a name was a nickname or an in-game name, and a
            // scoreboard only ever shows the latter.
            String gameName = riotGameName(player.getRiotId());
            roster.append("- ").append(participant.getSide())
                    .append(" — league nickname \"").append(player.getNickname()).append('"')
                    .append(" | in-game name ")
                    .append(gameName == null || gameName.isBlank()
                            ? "unknown (no Riot ID on file)"
                            : '"' + gameName + '"');
            // A match that went through the internal draft already knows who locked what. Telling the
            // model narrows champion identification from ~170 candidates to this player's one, which is
            // the difference between reading a 32px portrait and confirming it.
            String drafted = championNameById.get(participant.getChampionId());
            if (drafted != null) {
                roster.append(" — drafted champion: ").append(drafted);
                draftedNames.add(drafted);
            }
            roster.append('\n');
        }

        // Only the drafted ten when the draft filled every slot; otherwise the whole roster, and then
        // the atlas comparison is the only thing that can fill the champion column.
        boolean fullyDrafted = participantCount > 0 && draftedNames.size() == participantCount;
        String championNames = fullyDrafted
                ? String.join(", ", draftedNames)
                    + "\n(These ten are the ONLY champions in this match — every row must use one of them.)"
                : String.join(", ", champions.stream().map(Champion::getName).toList())
                    + "\n(This match was recorded manually, so no champion list narrows it down: identify"
                    + " each champion by comparing its row portrait against the reference atlases.)";
        String references = atlasCount == 0
                ? "No visual atlas is attached; use the canonical name list below."
                : "Images " + (screenshotCount + 1) + "-" + (screenshotCount + atlasCount)
                        + " are labelled CHAMPION REFERENCE ATLASES, not match screenshots.";

        return """
                ATTACHMENT ORDER:
                Images 1-%d are the only MATCH SCREENSHOTS from which match data may be extracted.
                %s

                EXPECTED ROSTER — LEAGUE NICKNAME ↔ IN-GAME NAME (the scoreboard shows the in-game name;
                report the league nickname, as the system prompt requires):
                %s
                CANONICAL CHAMPION NAMES:
                %s

                Extract all visible player rows and combine matching rows across tabs. Return winningSide,
                durationSeconds when visible, and players with name, champion, role, team, win, kills,
                deaths, assists, cs, gold, damage, vision, and largestMultiKill. Follow the JSON schema exactly.
                """.formatted(screenshotCount, references, roster, championNames);
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.2f MB", bytes / 1_048_576.0);
    }

    private static String traceContent(String content) {
        String trace = content == null ? "" : content.strip();
        return trace.length() > 6000 ? trace.substring(0, 6000) + "…" : trace;
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

    public record OcrLogEntry(String stage, String message) {}

    /**
     * @param unmatched          summoner names read from the screenshot that matched no roster player
     * @param missing            roster players no screenshot row could be found for
     * @param unmatchedChampions champion names the model returned that mapped to no champion
     */
    public record OcrDraft(String winningSide, Integer durationSeconds, List<OcrRow> rows,
                           List<String> unmatched, List<String> missing,
                           List<String> unmatchedChampions, List<OcrLogEntry> logs) {}
}
