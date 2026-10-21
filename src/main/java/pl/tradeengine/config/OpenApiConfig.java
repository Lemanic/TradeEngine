package pl.tradeengine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Metadata of the generated schema at {@code /v3/api-docs}, from which the dashboard repository
 * generates its TypeScript types. Swagger UI itself is only enabled on the dev profile.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tradeengineOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TradeEngine API")
                .version("1.0")
                .description("""
                        Portfolio: wartość portfela krypto ze snapshotów godzinowych.
                        Kształt odpowiedzi jest ustalony w docs/portfolio-api.md."""));
    }

    /**
     * Rewrites nullable object properties into a form a TypeScript generator reads correctly.
     *
     * <p>For a {@code @Schema(nullable = true)} property that is a reference, springdoc emits
     * {@code {"type":"null","$ref":"…/Ath"}}. Under OpenAPI 3.1 that reads as "this value is
     * the null type", so a generator produces {@code ath: null} instead of {@code Ath | null}.
     * The correct 3.1 spelling is an {@code anyOf} of the reference and null, which is what this
     * customizer substitutes.
     */
    @Bean
    public OpenApiCustomizer nullableReferencesAsAnyOf() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().values().forEach(schema -> {
                Map<String, Schema> properties = schema.getProperties();
                if (properties == null) {
                    return;
                }
                properties.replaceAll((name, property) -> isNullableReference(property)
                        ? anyOfReferenceOrNull(property)
                        : property);
            });
        };
    }

    private static boolean isNullableReference(Schema<?> property) {
        return property.get$ref() != null
                && property.getTypes() != null
                && property.getTypes().contains("null");
    }

    private static Schema<?> anyOfReferenceOrNull(Schema<?> property) {
        Schema<?> reference = new Schema<>().$ref(property.get$ref());
        // OpenAPI 3.1 keeps the type in `types`; setting `type` alone serializes as {}
        Schema<?> nullType = new Schema<>();
        nullType.setTypes(new LinkedHashSet<>(List.of("null")));

        Schema<?> rewritten = new Schema<>().anyOf(List.of(reference, nullType));
        rewritten.setDescription(property.getDescription());
        return rewritten;
    }
}
