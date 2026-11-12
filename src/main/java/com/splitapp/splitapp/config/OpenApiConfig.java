package com.splitapp.splitapp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH = "basicAuth";

    @Bean
    OpenAPI splitAppOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SplitApp API")
                        .version("1.0.0")
                        .description("""
                                REST API for splitting shared expenses within a group.

                                Register with POST /users, then authenticate every other request \
                                with HTTP Basic (email and password). Every POST must be sent with \
                                Content-Type: application/json. Errors are returned as \
                                application/problem+json. A 409 caused by concurrent changes can \
                                be retried."""))
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
