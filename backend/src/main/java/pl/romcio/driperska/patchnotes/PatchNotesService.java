package pl.romcio.driperska.patchnotes;

import java.util.List;
import org.springframework.stereotype.Service;
import pl.romcio.driperska.integration.discord.DiscordClient;
import pl.romcio.driperska.integration.discord.DiscordClient.Delivery;

/** Renders a patch-notes image and posts it (with an @everyone ping) to the Discord patch channel. */
@Service
public class PatchNotesService {

    private final PatchNotesImageGenerator generator;
    private final DiscordClient discord;

    public PatchNotesService(PatchNotesImageGenerator generator, DiscordClient discord) {
        this.generator = generator;
        this.discord = discord;
    }

    public Delivery announce(String version, String title, String date, List<String> changes) {
        byte[] png = generator.render(version, title, date, changes == null ? List.of() : changes);
        String caption = "@everyone\n🚀 **Driperska Liga " + version + "** — " + title
                + "\n📋 Co nowego — szczegóły na obrazku poniżej. Miłej gry! 🎮";
        return discord.sendPatchNotesImage(caption, png, "patch-" + slug(version) + ".png");
    }

    private static String slug(String version) {
        return version == null ? "notes" : version.replaceAll("[^A-Za-z0-9.]", "-");
    }
}
