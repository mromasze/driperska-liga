package pl.romcio.driperska.champion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A League of Legends champion, synced from Riot's Data Dragon static data. */
@Entity
@Table(name = "champion")
public class Champion {

    /** Riot numeric key (e.g. 266 = Aatrox). Assigned by Riot, not generated. */
    @Id
    private Integer id;

    /** Riot id / slug used to build image URLs (e.g. {@code Aatrox}, {@code MonkeyKing}). */
    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    private String title;

    /** Comma-separated Riot tags (e.g. {@code Fighter,Tank}). */
    @Column(length = 200)
    private String tags;

    @Column(name = "ddragon_version")
    private String ddragonVersion;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "splash_url", length = 500)
    private String splashUrl;

    @Column(name = "loading_url", length = 500)
    private String loadingUrl;

    protected Champion() {
    }

    public Champion(Integer id, String slug, String name) {
        this.id = id;
        this.slug = slug;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDdragonVersion() {
        return ddragonVersion;
    }

    public void setDdragonVersion(String ddragonVersion) {
        this.ddragonVersion = ddragonVersion;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getSplashUrl() {
        return splashUrl;
    }

    public void setSplashUrl(String splashUrl) {
        this.splashUrl = splashUrl;
    }

    public String getLoadingUrl() {
        return loadingUrl;
    }

    public void setLoadingUrl(String loadingUrl) {
        this.loadingUrl = loadingUrl;
    }
}
