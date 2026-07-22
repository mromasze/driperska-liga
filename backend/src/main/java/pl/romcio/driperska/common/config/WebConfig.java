package pl.romcio.driperska.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves uploaded media from the local volume (in production nginx serves it directly). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String mediaDir;

    public WebConfig(@Value("${app.storage.media-dir:./data/media}") String mediaDir) {
        this.mediaDir = mediaDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + (mediaDir.endsWith("/") ? mediaDir : mediaDir + "/");
        registry.addResourceHandler("/media/**").addResourceLocations(location);
    }
}
