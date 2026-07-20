package pl.romcio.driperska.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    public OpenAPI driperskaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Driperska Liga API")
                        .version("v1")
                        .description("REST API for the inhouse LoL league — results, ranking, players, "
                                + "match lifecycle and two-eyes approval."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .schemaRequirement(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }
}
