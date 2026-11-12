package escala_elderly_api;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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
 *   <li>Registra CorsFilter na camada de Servlet Filter para todo o prefixo /api/** lendo
 *       escala.cors.allowed-origins de application.properties, garantindo
 *       Access-Control-Allow-Origin inclusive em respostas 404/405/415 e bloqueio 403 em origens
 *       nao autorizadas quando restrito.
 *   <li>Endurece o Jackson 3 contra coercao silenciosa de tipos escalares (ex: float 3.7 truncado
 *       para int 3 ou boolean true convertido na string "true").
 * </ul>
 */
@Configuration
public class WebApiConfig {

    @Bean
    CorsFilter apiCorsFilter(@Value("${escala.cors.allowed-origins:*}") String rawAllowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(parseAllowedOrigins(rawAllowedOrigins));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(
                List.of("GET", "POST", "PATCH", "OPTIONS", "HEAD", "PUT", "DELETE"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }

    /**
     * Converte e valida a lista de origens CORS configurada em application.properties:
     *
     * <ul>
     *   <li>Remove barras finais '/' de origens explicitas (padrao RFC 6454 do header Origin).
     *   <li>Normaliza entradas nulas, vazias ou com '*' para List.of("*").
     *   <li>Falha imediatamente no boot (IllegalArgumentException) se uma origem explicita omitir o
     *       esquema http(s):// ou contiver espacos internos.
     * </ul>
     */
    static List<String> parseAllowedOrigins(String rawOrigins) {
        if (rawOrigins == null || rawOrigins.isBlank()) {
            return List.of("*");
        }

        List<String> origens = new ArrayList<>();
        boolean contemWildcard = false;

        for (String parte : rawOrigins.split(",")) {
            String token = parte.trim();
            if (token.isEmpty()) {
                continue;
            }
            if ("*".equals(token)) {
                contemWildcard = true;
                continue;
            }
            while (token.endsWith("/")) {
                token = token.substring(0, token.length() - 1);
            }
            validarOrigemHttpExplicita(token);
            if (!origens.contains(token)) {
                origens.add(token);
            }
        }

        if (contemWildcard || origens.isEmpty()) {
            return List.of("*");
        }
        return List.copyOf(origens);
    }

    private static void validarOrigemHttpExplicita(String origem) {
        boolean esquemaValido =
                (origem.startsWith("http://") && origem.length() > "http://".length())
                        || (origem.startsWith("https://") && origem.length() > "https://".length());
        boolean semEspacoInterno = origem.chars().noneMatch(Character::isWhitespace);
        if (!esquemaValido || !semEspacoInterno) {
            throw new IllegalArgumentException(
                    "Origem CORS invalida em escala.cors.allowed-origins: '"
                            + origem
                            + "'. Utilize '*' ou URLs iniciadas por http:// ou https://.");
        }

        try {
            URI uri = URI.create(origem);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException(
                        "Host ausente na origem CORS configurada: '" + origem + "'.");
            }
            boolean possuiComponentesProibidos =
                    (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                            || uri.getRawQuery() != null
                            || uri.getRawFragment() != null
                            || uri.getRawUserInfo() != null;
            if (possuiComponentesProibidos) {
                throw new IllegalArgumentException(
                        "Origem CORS deve conter apenas scheme://host[:port] segundo a RFC 6454: '"
                                + origem
                                + "'.");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Origem CORS malformada em escala.cors.allowed-origins: '" + origem + "'.", ex);
        }
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
