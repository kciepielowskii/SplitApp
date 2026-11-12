package com.splitapp.splitapp.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.LinkedHashMap;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every POST has to carry the JSON content type (see RequireJsonContentTypeFilter), also the
 * ones without a body. Swagger UI sends that header only for an operation that describes a
 * request body, so the POSTs without one get an optional empty JSON object. The server
 * ignores it.
 */
@Configuration
public class BodylessPostOpenApiConfig {

    @Bean
    OpenApiCustomizer jsonContentTypeForBodylessPosts() {
        return openApi -> openApi.getPaths().values().stream()
                .map(PathItem::getPost)
                .filter(post -> post != null && post.getRequestBody() == null)
                .forEach(BodylessPostOpenApiConfig::describeEmptyJsonBody);
    }

    private static void describeEmptyJsonBody(Operation post) {
        MediaType json = new MediaType()
                .schema(new ObjectSchema())
                .example(new LinkedHashMap<String, Object>());
        post.setRequestBody(new RequestBody()
                .description("Empty JSON object. The request must carry the JSON content type.")
                .required(false)
                .content(new Content().addMediaType("application/json", json)));
    }
}
