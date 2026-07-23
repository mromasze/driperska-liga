package pl.romcio.driperska.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
        assertThat(sheet.getWidth()).isEqualTo(1152);
        assertThat(sheet.getHeight()).isEqualTo(120);
        verify(dataDragon, times(1)).fetchChampionIcon("15.1.1", "Aatrox");
        verify(dataDragon, times(1)).fetchChampionIcon("15.1.1", "Ahri");
    }

    @Test
    void fallsBackImmediatelyWhenDataDragonIsUnavailable() {
        DataDragonClient dataDragon = mock(DataDragonClient.class);
        when(dataDragon.fetchChampionIcon("15.1.1", "Aatrox"))
                .thenThrow(new RestClientException("offline"));
        ChampionReferenceAtlasService service = new ChampionReferenceAtlasService(dataDragon);

        ChampionReferenceAtlasService.Atlas atlas = service.atlasFor(
                List.of(champion(266, "Aatrox", "Aatrox"), champion(103, "Ahri", "Ahri")));

        assertThat(atlas.images()).isEmpty();
        verify(dataDragon, times(1)).fetchChampionIcon("15.1.1", "Aatrox");
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
