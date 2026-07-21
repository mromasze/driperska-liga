package pl.romcio.driperska.integration.riot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "app.riot")
public class RiotProperties {
    private String apiKey;
    private String platform = "eun1";
    private String providerRegion = "EUNE";
    private String regionalRoute = "europe";
    private String callbackUrl;
    private String tournamentName = "Driperska Liga";
    private String mapType = "SUMMONERS_RIFT";
    private String pickType = "TOURNAMENT_DRAFT";
    private String spectatorType = "LOBBYONLY";
    private int teamSize = 5;
    /** When true, all Riot calls are faked (no real API) so the full flow can be tested offline. */
    private boolean mock = false;

    public boolean configured() { return StringUtils.hasText(apiKey); }
    public boolean isMock() { return mock; }
    public void setMock(boolean mock) { this.mock = mock; }
    public String tournamentBaseUrl() { return "https://" + platform + ".api.riotgames.com"; }
    public String regionalBaseUrl() { return "https://" + regionalRoute + ".api.riotgames.com"; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getProviderRegion() { return providerRegion; }
    public void setProviderRegion(String providerRegion) { this.providerRegion = providerRegion; }
    public String getRegionalRoute() { return regionalRoute; }
    public void setRegionalRoute(String regionalRoute) { this.regionalRoute = regionalRoute; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getTournamentName() { return tournamentName; }
    public void setTournamentName(String tournamentName) { this.tournamentName = tournamentName; }
    public String getMapType() { return mapType; }
    public void setMapType(String mapType) { this.mapType = mapType; }
    public String getPickType() { return pickType; }
    public void setPickType(String pickType) { this.pickType = pickType; }
    public String getSpectatorType() { return spectatorType; }
    public void setSpectatorType(String spectatorType) { this.spectatorType = spectatorType; }
    public int getTeamSize() { return teamSize; }
    public void setTeamSize(int teamSize) { this.teamSize = teamSize; }
}

