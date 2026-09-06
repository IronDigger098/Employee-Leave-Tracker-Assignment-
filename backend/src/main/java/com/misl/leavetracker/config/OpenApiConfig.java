package com.misl.leavetracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI / OpenAPI configuration.
 *
 * springdoc already discovers every endpoint, DTO and field by scanning the
 * controllers, so none of that is described here. This class only supplies the two
 * things it cannot infer from the code:
 *
 *   1. the document's title, version and description
 *   2. the fact that this API is secured with a JWT bearer token
 *
 * Point 2 is what makes the UI actually usable. Without a declared security scheme
 * Swagger has no "Authorize" button, every protected endpoint returns 401 when you
 * press "Try it out", and the page is a read-only list. With it, you log in once
 * through /api/auth/login, paste the token into Authorize, and every subsequent
 * request carries the header - the same job authInterceptor does in the Angular app.
 *
 * Available at http://localhost:8080/swagger-ui.html once the backend is running.
 */
@Configuration
public class OpenApiConfig {

    /** Referenced by both the scheme definition and the global requirement below. */
    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI leaveTrackerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Employee Leave Tracker API")
                        .version("1.0.0")
                        .description("""
                                REST API for the Employee Leave Tracker.

                                Every endpoint except POST /api/auth/login requires a JWT.
                                To try the protected endpoints:

                                  1. call POST /api/auth/login with a demo account
                                     (admin@misl.com / admin123, or rahim@misl.com / employee123)
                                  2. copy the `token` value from the response
                                  3. click Authorize above and paste it in
                                  4. requests from then on will carry the Bearer header

                                Endpoints marked ADMIN return 403 for an EMPLOYEE token.
                                """))

                /*
                 * Applies the scheme to every operation by default. Individual
                 * endpoints could opt out with @SecurityRequirements, but here only
                 * /api/auth/login is public and it simply ignores the header.
                 */
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))

                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                /*
                                 * bearerFormat is documentation only - it tells a
                                 * reader the token is a JWT. Swagger prepends
                                 * "Bearer " to whatever you paste either way.
                                 */
                                .bearerFormat("JWT")));
    }
}
