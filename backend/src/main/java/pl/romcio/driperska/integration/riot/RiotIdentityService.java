package pl.romcio.driperska.integration.riot;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.integration.riot.RiotApiClient.RiotAccount;
import pl.romcio.driperska.player.domain.Player;

@Service
public class RiotIdentityService {
    private final RiotApiClient client;
    private final RiotProperties properties;

    public RiotIdentityService(RiotApiClient client, RiotProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Transactional
    public Player resolve(Player player) {
        // Tournament-V5 identifies players by PUUID; summoner-v4 is intentionally NOT called
        // (it 404s cross-platform and adds an unnecessary failure mode).
        if (StringUtils.hasText(player.getRiotPuuid())) {
            return player;
        }
        if (properties.isMock()) {
            // Fake, deterministic identifier so the flow can be tested without real accounts.
            player.setRiotPuuid("mock-puuid-" + player.getId().toString().replace("-", ""));
            return player;
        }
        String riotId = player.getRiotId();
        int separator = riotId == null ? -1 : riotId.lastIndexOf('#');
        if (separator < 1 || separator == riotId.length() - 1) {
            throw new BusinessRuleException(
                    "Gracz " + player.getNickname() + " musi mieć Riot ID w formacie Nazwa#TAG");
        }
        RiotAccount account = client.resolveRiotId(
                riotId.substring(0, separator).trim(), riotId.substring(separator + 1).trim());
        player.setRiotPuuid(account.puuid());
        return player;
    }
}

