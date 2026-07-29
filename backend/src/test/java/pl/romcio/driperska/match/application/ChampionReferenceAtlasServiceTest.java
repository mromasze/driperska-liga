package pl.romcio.driperska.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import pl.romcio.driperska.champion.domain.Champion;
import pl.romcio.driperska.champion.infra.DataDragonClient;

class ChampionReferenceAtlasServiceTest {

    @Test
    void rendersLabelledAtlasAndCachesItForTheSameChampionVersion() throws Exception {
        DataDragonClient dataDragon = mock(DataDragonClient.class);
        byte[] portrait = portraitPng();
        when(dataDragon.fetchChampionIcon("15.1.1", "Aatrox")).thenReturn(portrait);
        when(dataDragon.fetchChampionIcon("15.1.1", "Ahri")).thenReturn(portrait);
        ChampionReferenceAtlasService service = new ChampionReferenceAtlasService(dataDragon);

        Champion aatrox = champion(266, "Aatrox", "Aatrox");
        Champion ahri = champion(103, "Ahri", "Ahri");
        ChampionReferenceAtlasService.Atlas first = service.atlasFor(List.of(ahri, aatrox));
        ChampionReferenceAtlasService.Atlas second = service.atlasFor(List.of(ahri, aatrox));

        assertThat(first.images()).hasSize(1);
        assertThat(first.portraitCount()).isEqualTo(2);
        assertThat(first.version()).isEqualTo("15.1.1");
        assertThat(second).isSameAs(first);
        BufferedImage sheet = ImageIO.read(new ByteArrayInputStream(
                Base64.getDecoder().decode(first.images().getFirst())));
        // A sheet has to stay inside a vision model's input-resize budget, otherwise every portrait on
        // it gets shrunk before the model ever compares anything. This is the point of the geometry.
        assertThat(sheet.getWidth()).isLessThanOrEqualTo(1024);
        assertThat(sheet.getHeight()).isLessThanOrEqualTo(1024);
        assertThat(sheet.getWidth()).isEqualTo(8 * 128);
        verify(dataDragon, times(1)).fetchChampionIcon("15.1.1", "Aatrox");
        verify(dataDragon, times(1)).fetchChampionIcon("15.1.1", "Ahri");
    }

    /** 170 champions must not need 170 sheets — nor fit on one where nothing is legible. */
    @Test
    void splitsAFullRosterAcrossSheetsOfForty() throws Exception {
        DataDragonClient dataDragon = mock(DataDragonClient.class);
        when(dataDragon.fetchChampionIcon(eq("15.1.1"), anyString())).thenReturn(portraitPng());
        ChampionReferenceAtlasService service = new ChampionReferenceAtlasService(dataDragon);

        List<Champion> roster = new ArrayList<>();
        for (int i = 0; i < 170; i++) {
            roster.add(champion(1000 + i, "Champ" + i, "Champ " + i));
        }

        ChampionReferenceAtlasService.Atlas atlas = service.atlasFor(roster);

        assertThat(atlas.portraitCount()).isEqualTo(170);
        assertThat(atlas.images()).hasSize(5); // ceil(170 / 40)
        for (String image : atlas.images()) {
            BufferedImage sheet = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(image)));
            assertThat(sheet.getWidth()).isLessThanOrEqualTo(1024);
            assertThat(sheet.getHeight()).isLessThanOrEqualTo(1024);
        }
    }

    /**
     * A champion whose slug 404s — Riot renames one occasionally, and it then fails forever — used to
     * end the download, truncating the atlas to whatever sorted before it. Everything after Aatrox
     * simply vanished from the model's reference.
     */
    @Test
    void oneMissingPortraitDoesNotTruncateTheAtlas() throws Exception {
        DataDragonClient dataDragon = mock(DataDragonClient.class);
        when(dataDragon.fetchChampionIcon(eq("15.1.1"), anyString())).thenReturn(portraitPng());
        when(dataDragon.fetchChampionIcon("15.1.1", "Ahri")).thenThrow(new RestClientException("404"));
        ChampionReferenceAtlasService service = new ChampionReferenceAtlasService(dataDragon);

        ChampionReferenceAtlasService.Atlas atlas = service.atlasFor(List.of(
                champion(266, "Aatrox", "Aatrox"),
                champion(103, "Ahri", "Ahri"),
                champion(32, "Amumu", "Amumu"),
                champion(34, "Anivia", "Anivia")));

        assertThat(atlas.portraitCount()).isEqualTo(3); // everything except Ahri
        assertThat(atlas.images()).hasSize(1);
    }

    @Test
    void fallsBackToTextOnlyWhenDataDragonIsWhollyUnavailable() {
        DataDragonClient dataDragon = mock(DataDragonClient.class);
        when(dataDragon.fetchChampionIcon(eq("15.1.1"), anyString()))
                .thenThrow(new RestClientException("offline"));
        ChampionReferenceAtlasService service = new ChampionReferenceAtlasService(dataDragon);

        ChampionReferenceAtlasService.Atlas atlas = service.atlasFor(
                List.of(champion(266, "Aatrox", "Aatrox"), champion(103, "Ahri", "Ahri")));

        assertThat(atlas.images()).isEmpty();
        assertThat(atlas.portraitCount()).isZero();
    }

    private static Champion champion(int id, String slug, String name) {
        Champion champion = new Champion(id, slug, name);
        champion.setDdragonVersion("15.1.1");
        return champion;
    }

    private static byte[] portraitPng() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.MAGENTA);
        graphics.fillRect(0, 0, 64, 64);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
