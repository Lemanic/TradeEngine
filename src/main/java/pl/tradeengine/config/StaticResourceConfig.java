package pl.tradeengine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.Arrays;

/**
 * Cache headers for the dashboard's static files.
 *
 * <p>The build puts hashed bundles under {@code /assets/} and references them from
 * {@code index.html}. Those two need opposite policies: a bundle whose name contains its content
 * hash can be cached forever, while {@code index.html} must never be cached, or the first deploy
 * after a change leaves the browser with an old document pointing at hashes that no longer exist
 * — a white screen until a hard refresh.
 *
 * <p>Spring ships neither policy: measured with {@code spring.devtools.add-properties=false}
 * (i.e. the way the production jar behaves) static files come back with no {@code Cache-Control}
 * at all, only {@code Last-Modified}, which leaves the browser to cache heuristically. So
 * {@code index.html} gets {@code no-store} from {@code application-prod.yml} and the bundles get
 * their long life here.
 *
 * <p>Pliki w korzeniu buildu, które nie mają hasha w nazwie — dziś tylko favicon — dostają
 * krótki {@code max-age}: {@code immutable} byłoby kłamstwem, bo nazwa się nie zmienia przy
 * podmianie treści, a {@code no-store} kazałby pobierać je przy każdym wejściu.
 *
 * <p>Locations are derived from {@code spring.web.resources.static-locations} so this stays in
 * step with wherever the dashboard is deployed.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final Duration FOREVER = Duration.ofDays(365);
    private static final Duration ONE_DAY = Duration.ofDays(1);

    private final String[] staticLocations;

    public StaticResourceConfig(
            @Value("${spring.web.resources.static-locations:classpath:/static/}") String[] staticLocations) {
        this.staticLocations = staticLocations;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // More specific than the default /** handler, so this mapping wins for bundles.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(assetLocations())
                .setCacheControl(CacheControl.maxAge(FOREVER).cachePublic().immutable());

        registry.addResourceHandler("/favicon.svg", "/favicon.ico")
                .addResourceLocations(staticLocations)
                .setCacheControl(CacheControl.maxAge(ONE_DAY).cachePublic());
    }

    private String[] assetLocations() {
        return Arrays.stream(staticLocations)
                .map(location -> location.endsWith("/") ? location + "assets/" : location + "/assets/")
                .toArray(String[]::new);
    }
}
