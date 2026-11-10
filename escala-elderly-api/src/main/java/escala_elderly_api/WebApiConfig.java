package escala_elderly_api;

import java.util.List;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Configuracao web transversal da API REST (ADR-BE-001 e ADR-BE-010):
 *
 * <ul>
 *   <li>Registra CorsFilter na camada de Servlet Filter para todo o prefixo /api/**, garantindo
 *       Access-Control-Allow-Origin inclusive em respostas 404/405/415 antes do DispatcherServlet.
 *   <li>Endurece o Jackson 3 contra coercao silenciosa de tipos escalares (ex: float 3.7 truncado
 *       para int 3 ou boolean true convertido na string "true").
 * </ul>
 */
@Configuration
public class WebApiConfig {

    @Bean
    CorsFilter apiCorsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(
                List.of("GET", "POST", "PATCH", "OPTIONS", "HEAD", "PUT", "DELETE"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }

    @Bean
    JsonMapperBuilderCustomizer strictScalarJsonCustomizer() {
        return builder ->
                builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .withCoercionConfig(
                                LogicalType.Textual,
                                cfg -> {
                                    cfg.setCoercion(
                                            CoercionInputShape.Boolean, CoercionAction.Fail);
                                    cfg.setCoercion(
                                            CoercionInputShape.Integer, CoercionAction.Fail);
                                    cfg.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                                })
                        .withCoercionConfig(
                                LogicalType.Integer,
                                cfg -> {
                                    cfg.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
                                    cfg.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                                    cfg.setCoercion(
                                            CoercionInputShape.Boolean, CoercionAction.Fail);
                                    cfg.setCoercion(
                                            CoercionInputShape.EmptyString, CoercionAction.Fail);
                                });
    }
}
