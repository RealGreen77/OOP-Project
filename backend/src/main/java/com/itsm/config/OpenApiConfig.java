package com.itsm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html with JWT "Authorize" support. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI itsmOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("I-ITSM API")
                        .version("1.0.0")
                        .description("Intelligent IT Service Management & Incident Resolution System. "
                                + "Log in via POST /api/auth/login, then click Authorize and paste the token."))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
