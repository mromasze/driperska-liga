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
    /** Tournament APIs are served from the regional cluster (americas), not the platform host. */
    private String tournamentRoute = "americas";
    /**
     * When true, use tournament-STUB-v5 (available to development keys; codes are NOT playable
     * in-client). Set false only with a production key that has the real tournament-v5 product.
     */
    private boolean useStub = true;
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
    public boolean isUseStub() { return useStub; }
    public void setUseStub(boolean useStub) { this.useStub = useStub; }
    public String getTournamentRoute() { return tournamentRoute; }
    public void setTournamentRoute(String tournamentRoute) { this.tournamentRoute = tournamentRoute; }
    /** Base URL for tournament(-stub) endpoints, e.g. https://americas.api.riotgames.com/lol/tournament-stub/v5 */
    public String tournamentApiBase() {
        return "https://" + tournamentRoute + ".api.riotgames.com/lol/"
                + (useStub ? "tournament-stub" : "tournament") + "/v5";
    }
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

