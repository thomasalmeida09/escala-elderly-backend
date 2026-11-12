package escala_elderly_api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class EscalaElderlyApiApplicationTests {

    private static final String VITE_ORIGIN = "http://localhost:5173";

    @Autowired private MockMvc mockMvc;

    @Autowired private CuidadorService cuidadorService;

    @BeforeEach
    void restaurarEstadoLimpoAntesDeCadaTeste() {
        cuidadorService.restaurarEscalaInicial();
    }

    @Test
    @DisplayName("Contexto Spring Boot carrega sem erros")
    void contextLoads() {}

    @Test
    @DisplayName(
            "GET /api/cuidadores retorna 4 cuidadores canonicos (id=3 Carlos Rocha), omite nulos e inclui CORS")
    void deveListarCuidadoresComContratoCanonicoAlinhadoAoFrontend() throws Exception {
        mockMvc.perform(get("/api/cuidadores").header("Origin", VITE_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("Ana Souza"))
                .andExpect(jsonPath("$[0].status").value("Presente"))
                .andExpect(jsonPath("$[0].turno").value("Diurno (07h-19h)"))
                .andExpect(jsonPath("$[0].quarto").value("Quarto 01"))
                .andExpect(jsonPath("$[0].substituto").doesNotExist())
                .andExpect(jsonPath("$[0].telefone").doesNotExist())
                .andExpect(jsonPath("$[0].horasDescanso").doesNotExist())
                .andExpect(content().string(not(containsString("null"))))
                .andExpect(content().string(not(containsString("\"substituto\""))))
                .andExpect(content().string(not(containsString("\"telefone\""))))
                .andExpect(content().string(not(containsString("\"horasDescanso\""))))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].nome").value("Lucas Lima"))
                .andExpect(jsonPath("$[1].status").value("Presente"))
                .andExpect(jsonPath("$[2].id").value(3))
                .andExpect(jsonPath("$[2].nome").value("Carlos Rocha"))
                .andExpect(jsonPath("$[2].status").value("Presente"))
                .andExpect(jsonPath("$[2].turno").value("Noturno (19h-07h)"))
                .andExpect(jsonPath("$[2].quarto").value("Quarto 03"))
                .andExpect(jsonPath("$[3].id").value(4))
                .andExpect(jsonPath("$[3].nome").value("Mariana Costa"))
                .andExpect(jsonPath("$[3].status").value("Presente"));
    }

    @Test
    @DisplayName(
            "PATCH /api/cuidadores/3/status persiste falta/cobertura em Carlos Rocha e limpa substituto ao voltar a Presente")
    void deveAtualizarStatusEPersistirEmMemoriaLimpandoSubstituto() throws Exception {
        mockMvc.perform(
                        patch("/api/cuidadores/3/status")
                                .header("Origin", VITE_ORIGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"Faltou\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.nome").value("Carlos Rocha"))
                .andExpect(jsonPath("$.status").value("Faltou"))
                .andExpect(jsonPath("$.substituto").doesNotExist())
                .andExpect(content().string(not(containsString("null"))))
                .andExpect(content().string(not(containsString("\"substituto\""))));

        // Verifica que Mariana Costa (id=4) permanece Presente e Carlos (id=3) permanece Faltou
        mockMvc.perform(get("/api/cuidadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].status").value("Faltou"))
                .andExpect(jsonPath("$[3].nome").value("Mariana Costa"))
                .andExpect(jsonPath("$[3].status").value("Presente"));

        // Aloca cobertura com substituto
        mockMvc.perform(
                        patch("/api/cuidadores/3/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"status\":\"Cobertura\",\"substituto\":\"  Folguista Turno A  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Cobertura"))
                .andExpect(jsonPath("$.substituto").value("Folguista Turno A"));

        // Retorna Carlos a Presente -> substituto deve ser omitido do JSON
        mockMvc.perform(
                        patch("/api/cuidadores/3/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"status\":\"Presente\",\"substituto\":\"Folguista Turno A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Presente"))
                .andExpect(jsonPath("$.substituto").doesNotExist())
                .andExpect(content().string(not(containsString("null"))))
                .andExpect(content().string(not(containsString("\"substituto\""))));
    }

    @Test
    @DisplayName(
            "POST /api/cuidadores/batch sincroniza lote CSV (com Auto-ID) e POST /reset restaura os 4 iniciais")
    void deveSincronizarLoteCsvERestaurarEscalaComUmClique() throws Exception {
        String loteCsvJson =
                """
                [
                  {
                    "id": 10,
                    "nome": "Cuidador Sintetico 10",
                    "status": "Presente",
                    "turno": "Diurno (07h-19h)",
                    "quarto": "Quarto 01",
                    "telefone": "+55 11 90000-0010",
                    "horasDescanso": 24
                  },
                  {
                    "nome": "Folguista Turno B",
                    "status": "Folga",
                    "turno": "Noturno (19h-07h)",
                    "quarto": "Reserva",
                    "telefone": "+55 11 90000-0011",
                    "horasDescanso": 36
                  }
                ]
                """;

        mockMvc.perform(
                        post("/api/cuidadores/batch")
                                .header("Origin", VITE_ORIGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loteCsvJson))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].telefone").value("+55 11 90000-0010"))
                .andExpect(jsonPath("$[0].horasDescanso").value(24))
                .andExpect(jsonPath("$[1].id").value(11))
                .andExpect(jsonPath("$[1].nome").value("Folguista Turno B"))
                .andExpect(content().string(not(containsString("null"))))
                .andExpect(content().string(not(containsString("\"substituto\""))));

        mockMvc.perform(get("/api/cuidadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // POST /reset sem body restaura os 4 cuidadores canonicos
        mockMvc.perform(post("/api/cuidadores/reset").header("Origin", VITE_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[2].id").value(3))
                .andExpect(jsonPath("$[2].nome").value("Carlos Rocha"))
                .andExpect(jsonPath("$[2].status").value("Presente"))
                .andExpect(content().string(not(containsString("null"))))
                .andExpect(content().string(not(containsString("\"substituto\""))));
    }

    @Test
    @DisplayName(
            "GET /api/cuidadores?privacy=true mascara nomes, substitutos e telefones preservando horasDescanso e sem mutar snapshot (S12, S13, S14, S15)")
    void deveExporProjecaoLgpdEmModoPrivacidadeEBloquearQueryParamInvalidoOuHpp() throws Exception {
        String loteComPiiSintetica =
                """
                [
                  {
                    "id": 3,
                    "nome": "Carlos Rocha",
                    "status": "Cobertura",
                    "turno": "Noturno (19h-07h)",
                    "quarto": "Quarto 03",
                    "telefone": "+55 11 90000-0010",
                    "horasDescanso": 8,
                    "substituto": "João da Conceição"
                  },
                  {
                    "id": 4,
                    "nome": "Mariana Costa",
                    "status": "Folga",
                    "turno": "Diurno (07h-19h)",
                    "quarto": "Quarto 04",
                    "telefone": "(11) 90000-0011 ramal 12",
                    "horasDescanso": 24
                  }
                ]
                """;

        mockMvc.perform(
                        post("/api/cuidadores/batch")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loteComPiiSintetica))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cuidadores").param("privacy", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].nome").value("Carlos R."))
                .andExpect(jsonPath("$[0].substituto").value("João C."))
                .andExpect(jsonPath("$[0].telefone").value("(••) •••••-••••"))
                .andExpect(jsonPath("$[0].horasDescanso").value(8))
                .andExpect(jsonPath("$[1].id").value(4))
                .andExpect(jsonPath("$[1].nome").value("Mariana C."))
                .andExpect(jsonPath("$[1].telefone").value("(••) •••••-••••"))
                .andExpect(jsonPath("$[1].horasDescanso").value(24))
                .andExpect(content().string(not(containsString("Rocha"))))
                .andExpect(content().string(not(containsString("Conceição"))))
                .andExpect(content().string(not(containsString("90000"))));

        // Leitura subsequente com ?privacy=false ou sem parametro comprova snapshot intacto
        mockMvc.perform(get("/api/cuidadores").param("privacy", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Carlos Rocha"))
                .andExpect(jsonPath("$[0].substituto").value("João da Conceição"))
                .andExpect(jsonPath("$[0].telefone").value("+55 11 90000-0010"))
                .andExpect(jsonPath("$[1].telefone").value("(11) 90000-0011 ramal 12"));

        // S15: Rejeita ?privacy vazio, valores nao-booleanos e HTTP Parameter Pollution
        for (String valorInvalido :
                new String[] {"", "   ", "1", "0", "sim", "banana", "true,false"}) {
            mockMvc.perform(get("/api/cuidadores").param("privacy", valorInvalido))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
        }

        mockMvc.perform(get("/api/cuidadores?privacy=false&privacy=true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));

        mockMvc.perform(get("/api/cuidadores?privacy=true&privacy=true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));

        // C1: Prova HTTP end-to-end na fronteira exata de 120 caracteres (nome e substituto <= 120
        // em ?privacy=true)
        String limite120 = "A".repeat(118) + " B";
        String loteFronteira120 =
                "[{\"id\":3,\"nome\":\""
                        + limite120
                        + "\",\"status\":\"Cobertura\",\"substituto\":\""
                        + limite120
                        + "\"}]";
        mockMvc.perform(
                        post("/api/cuidadores/batch")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loteFronteira120))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value(limite120))
                .andExpect(jsonPath("$[0].substituto").value(limite120));

        String esperadoMascarado120 = "A".repeat(117) + " B.";
        mockMvc.perform(get("/api/cuidadores").param("privacy", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value(esperadoMascarado120))
                .andExpect(jsonPath("$[0].substituto").value(esperadoMascarado120));
    }

    @Test
    @DisplayName(
            "POST /batch e PATCH /status higienizam CSV Injection, aceitam DDD entre parenteses/ramais e rejeitam formulas em status/telefone (S01..S08, S16)")
    void deveHigienizarCsvInjectionEAplicarGramaticaSimetricaDeTelefoneEmHttp() throws Exception {
        String loteHostilSanitizavel =
                """
                [
                  {
                    "id": 1,
                    "nome": " =+=-@CMD|' /C calc'!A0",
                    "status": "Cobertura",
                    "turno": "\\t=Diurno (07h-19h)",
                    "quarto": "---",
                    "telefone": "(11) 3000-0106 ramal 12",
                    "horasDescanso": 12,
                    "substituto": "\\uFEFF=Folguista\\r\\nTurno A"
                  },
                  {
                    "id": 2,
                    "nome": "Ana\\u0085Souza",
                    "status": "Folga",
                    "turno": "Noturno (19h-07h)",
                    "quarto": "Quarto 02",
                    "telefone": "'+55 (11) 90000-0010",
                    "horasDescanso": 24
                  }
                ]
                """;

        mockMvc.perform(
                        post("/api/cuidadores/batch")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loteHostilSanitizavel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("CMD|' /C calc'!A0"))
                .andExpect(jsonPath("$[0].turno").value("Diurno (07h-19h)"))
                .andExpect(jsonPath("$[0].quarto").doesNotExist())
                .andExpect(jsonPath("$[0].telefone").value("(11) 3000-0106 ramal 12"))
                .andExpect(jsonPath("$[0].substituto").value("Folguista Turno A"))
                .andExpect(jsonPath("$[1].nome").value("Ana Souza"))
                .andExpect(jsonPath("$[1].telefone").value("+55 (11) 90000-0010"));

        // S04: PATCH tambem sanitiza substituto contra CSV Injection
        mockMvc.perform(
                        patch("/api/cuidadores/1/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"Cobertura\",\"substituto\":\"=CMD()\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.substituto").value("CMD()"));

        // S08: status com prefixo "=Presente" falha com 400 INVALID_STATUS (jamais sanitizado)
        mockMvc.perform(
                        patch("/api/cuidadores/1/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"=Presente\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));

        // S05 & S07: nome esvaziado e telefones com formula/notacao cientifica/mascara falham 400
        for (String payloadInvalido :
                new String[] {
                    "[{\"id\":1,\"nome\":\"===++--\",\"status\":\"Presente\"}]",
                    "[{\"id\":1,\"nome\":\"Ana Souza\",\"status\":\"Presente\",\"telefone\":\"+CMD()\"}]",
                    "[{\"id\":1,\"nome\":\"Ana Souza\",\"status\":\"Presente\",\"telefone\":\"=HYPERLINK(\\\"http://evil.example\\\")\"}]",
                    "[{\"id\":1,\"nome\":\"Ana Souza\",\"status\":\"Presente\",\"telefone\":\"5,51E+12\"}]",
                    "[{\"id\":1,\"nome\":\"Ana Souza\",\"status\":\"Presente\",\"telefone\":\"(11 90000-0010\"}]",
                    "[{\"id\":1,\"nome\":\"Ana Souza\",\"status\":\"Presente\",\"telefone\":\"(••) •••••-••••\"}]"
                }) {
            mockMvc.perform(
                            post("/api/cuidadores/batch")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payloadInvalido))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
        }

        // S16: ApiExceptionHandler distingue 'id' (INVALID_ID) de outros parametros
        // (INVALID_PAYLOAD)
        ApiExceptionHandler handler = new ApiExceptionHandler();
        Method metodoAlvo =
                CuidadorController.class.getMethod(
                        "atualizarStatus", Long.class, StatusUpdateRequest.class);
        MethodParameter param = new MethodParameter(metodoAlvo, 0);
        MockHttpServletRequest mockReq = new MockHttpServletRequest("GET", "/api/cuidadores");

        MethodArgumentTypeMismatchException mismatchOutroParam =
                new MethodArgumentTypeMismatchException(
                        "invalido",
                        Boolean.class,
                        "privacy",
                        param,
                        new IllegalArgumentException());
        ResponseEntity<ApiErrorResponse> respOutro =
                handler.tratarTipoDeParametroInvalido(mismatchOutroParam, mockReq);
        assertEquals(400, respOutro.getStatusCode().value());
        assertEquals("INVALID_PAYLOAD", respOutro.getBody().code());
    }

    @Test
    @DisplayName(
            "WebApiConfig.parseAllowedOrigins normaliza barras finais e falha rapido em origens sem esquema HTTP (M4.1)")
    void deveNormalizarOrigensCorsERejeitarConfiguracaoMalformada() {
        assertEquals(List.of("*"), WebApiConfig.parseAllowedOrigins(null));
        assertEquals(List.of("*"), WebApiConfig.parseAllowedOrigins("   "));
        assertEquals(List.of("*"), WebApiConfig.parseAllowedOrigins("*"));
        assertEquals(
                List.of("http://localhost:5173", "https://app.escala.example.com"),
                WebApiConfig.parseAllowedOrigins(
                        " http://localhost:5173/ , https://app.escala.example.com/// "));

        for (String origemInvalida :
                new String[] {
                    "localhost:5173",
                    "ftp://app.escala.example.com",
                    "http://",
                    "https://",
                    "http://localhost:5173 /api",
                    "https://app.escala.example.com/painel",
                    "https://app.escala.example.com?env=prod",
                    "https://app.escala.example.com#secao",
                    "https://admin@example.com"
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> WebApiConfig.parseAllowedOrigins(origemInvalida));
        }
    }

    @Test
    @DisplayName(
            "Bateria adversaria HTTP valida 400/404/405/415, bloqueia coercao escalar Jackson 3 e preserva snapshot")
    void deveRejeitarPayloadsAdversariosECoercoesSilenciosasComSchemaUniforme() throws Exception {
        // 404 para ID valido inexistente
        mockMvc.perform(
                        patch("/api/cuidadores/999/status")
                                .header("Origin", VITE_ORIGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"Folga\"}"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.code").value("CUIDADOR_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/cuidadores/999/status"))
                .andExpect(jsonPath("$.timestamp").isString());

        // 400 precede 404 quando status e invalido mesmo em ID 999
        mockMvc.perform(
                        patch("/api/cuidadores/999/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"Trabalhando\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));

        // 400 INVALID_ID para path nao-numerico, zero e acima de MAX_SAFE_JS_ID (2^53)
        mockMvc.perform(
                        patch("/api/cuidadores/abc/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"Presente\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ID"));

        mockMvc.perform(
                        patch("/api/cuidadores/0/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"Presente\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ID"));

        mockMvc.perform(
                        patch("/api/cuidadores/9007199254740992/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"Presente\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ID"));

        // 400 MALFORMED_JSON para JSON truncado, literal null e coercao escalar indevida
        for (String badPatchBody :
                new String[] {
                    "{\"status\":",
                    "null",
                    "{\"status\":\"Cobertura\",\"substituto\":true}",
                    "{\"status\":123}"
                }) {
            mockMvc.perform(
                            patch("/api/cuidadores/3/status")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(badPatchBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                    .andExpect(jsonPath("$.message", not(containsString("tools.jackson"))));
        }

        for (String coercedBatchBody :
                new String[] {
                    "[{\"id\":3.7,\"nome\":\"Cuidador Sintetico\",\"status\":\"Presente\"}]",
                    "[{\"id\":\"3\",\"nome\":\"Cuidador Sintetico\",\"status\":\"Presente\"}]",
                    "[{\"id\":1,\"nome\":\"Cuidador Sintetico\",\"status\":\"Presente\",\"horasDescanso\":11.5}]",
                    "[{\"id\":1,\"nome\":999,\"status\":\"Presente\"}]"
                }) {
            mockMvc.perform(
                            post("/api/cuidadores/batch")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(coercedBatchBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
        }

        // Lote [null], vazio [] e lote com 2o item invalido preservam os 4 cuidadores originais
        mockMvc.perform(
                        post("/api/cuidadores/batch")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[null]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));

        mockMvc.perform(
                        post("/api/cuidadores/batch")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));

        mockMvc.perform(
                        post("/api/cuidadores/batch")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "[{\"id\":1,\"nome\":\"Cuidador Ok\",\"status\":\"Folga\"},{\"id\":2,\"nome\":\"Cuidador Ruim\",\"status\":\"Invalido\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));

        mockMvc.perform(get("/api/cuidadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].nome").value("Ana Souza"));

        // 405, 415 e 404 de rota nao mapeada com CORS garantido em /api/**
        mockMvc.perform(delete("/api/cuidadores").header("Origin", VITE_ORIGIN))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(
                        patch("/api/cuidadores/3/status")
                                .header("Origin", VITE_ORIGIN)
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("status=Faltou"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        mockMvc.perform(get("/api/cuidadores/rota/inexistente").header("Origin", VITE_ORIGIN))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
    }

    @Test
    @DisplayName(
            "Preflight CORS OPTIONS responde 200 OK para GET, PATCH e POST vindos do Vite localhost:5173")
    void deveResponderPreflightCorsParaGetPatchEPost() throws Exception {
        mockMvc.perform(
                        options("/api/cuidadores")
                                .header("Origin", VITE_ORIGIN)
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));

        mockMvc.perform(
                        options("/api/cuidadores/3/status")
                                .header("Origin", VITE_ORIGIN)
                                .header("Access-Control-Request-Method", "PATCH")
                                .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(
                        header().string("Access-Control-Allow-Methods", containsString("PATCH")));

        mockMvc.perform(
                        options("/api/cuidadores/batch")
                                .header("Origin", VITE_ORIGIN)
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));

        mockMvc.perform(
                        options("/api/cuidadores/reset")
                                .header("Origin", VITE_ORIGIN)
                                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));

        mockMvc.perform(
                        options("/api/cuidadores/stream")
                                .header("Origin", VITE_ORIGIN)
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    @DisplayName(
            "GET /api/cuidadores/stream com ?privacy invalido ou HPP responde 400 JSON mesmo sob Accept: text/event-stream, e ApiExceptionHandler blinda respostas async comitadas (S09, S17)")
    void deveRejeitarPrivacyInvalidoNoStreamComAcceptSseEBlindarRespostasAsyncComitadas()
            throws Exception {
        for (String valorInvalido :
                new String[] {"", "   ", "1", "0", "sim", "banana", "true,false"}) {
            mockMvc.perform(
                            get("/api/cuidadores/stream")
                                    .param("privacy", valorInvalido)
                                    .header("Origin", VITE_ORIGIN)
                                    .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                    .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
        }

        mockMvc.perform(
                        get("/api/cuidadores/stream?privacy=false&privacy=true")
                                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));

        mockMvc.perform(
                        get("/api/cuidadores/stream?privacy=true&privacy=true")
                                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));

        assertEquals(0, cuidadorService.contarAssinantesStreamAtivos());

        // Accept incompativel em rota JSON normal retorna 406 NOT_ACCEPTABLE estruturado
        mockMvc.perform(get("/api/cuidadores").header("Accept", MediaType.APPLICATION_XML_VALUE))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));

        // S17: Blindagem de ApiExceptionHandler para respostas async/SSE ja comitadas
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest reqStream =
                new MockHttpServletRequest("GET", "/api/cuidadores/stream");

        assertNull(
                handler.tratarConexaoAssincronaEncerradaPeloCliente(
                        new AsyncRequestNotUsableException("Client closed stream")));

        MockHttpServletResponse respNaoComitada = new MockHttpServletResponse();
        ResponseEntity<Void> timeoutNaoComitado =
                handler.tratarTimeoutAssincrono(
                        new AsyncRequestTimeoutException(), respNaoComitada);
        assertEquals(503, timeoutNaoComitado.getStatusCode().value());

        MockHttpServletResponse respComitada = new MockHttpServletResponse();
        respComitada.setCommitted(true);
        assertNull(
                handler.tratarTimeoutAssincrono(new AsyncRequestTimeoutException(), respComitada));
        assertNull(
                handler.tratarErroInternoInesperado(
                        new IOException("Broken pipe"), reqStream, respComitada));

        MockHttpServletResponse respSse = new MockHttpServletResponse();
        respSse.setContentType("text/event-stream;charset=UTF-8");
        assertNull(
                handler.tratarErroInternoInesperado(
                        new IOException("Stream reset"), reqStream, respSse));

        ResponseEntity<ApiErrorResponse> erroNormal =
                handler.tratarErroInternoInesperado(new IllegalStateException("Falha"), reqStream);
        assertEquals(500, erroNormal.getStatusCode().value());

        ResponseEntity<ApiErrorResponse> naoAceitavel =
                handler.tratarMediaTypeNaoAceitavel(
                        new HttpMediaTypeNotAcceptableException(
                                List.of(MediaType.APPLICATION_JSON)),
                        reqStream);
        assertEquals(406, naoAceitavel.getStatusCode().value());
    }

    @Nested
    @SpringBootTest(
            properties =
                    "escala.cors.allowed-origins=http://localhost:5173,https://app.escala.example.com/")
    @AutoConfigureMockMvc
    @DisplayName("Perfil de CORS restrito via escala.cors.allowed-origins (S17, S18)")
    class RestrictedCorsOriginsProfileTest {

        @Autowired private MockMvc restrictedMockMvc;

        @Autowired private CuidadorService restrictedCuidadorService;

        @BeforeEach
        void restaurarEscalaLimpa() {
            restrictedCuidadorService.restaurarEscalaInicial();
        }

        @Test
        @DisplayName(
                "Aceita origens autorizadas (normalizando barra final) e bloqueia origem intrusa com 403 sem mutar estado")
        void deveAceitarOrigensAutorizadasEBloquearOrigemIntrusaCom403SemEfeitoColateral()
                throws Exception {
            restrictedMockMvc
                    .perform(get("/api/cuidadores").header("Origin", VITE_ORIGIN))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", VITE_ORIGIN))
                    .andExpect(header().string("Vary", containsString("Origin")));

            restrictedMockMvc
                    .perform(
                            get("/api/cuidadores")
                                    .header("Origin", "https://app.escala.example.com"))
                    .andExpect(status().isOk())
                    .andExpect(
                            header().string(
                                            "Access-Control-Allow-Origin",
                                            "https://app.escala.example.com"))
                    .andExpect(header().string("Vary", containsString("Origin")));

            restrictedMockMvc
                    .perform(
                            options("/api/cuidadores/3/status")
                                    .header("Origin", "https://evil.example.com")
                                    .header("Access-Control-Request-Method", "PATCH"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

            restrictedMockMvc
                    .perform(
                            patch("/api/cuidadores/3/status")
                                    .header("Origin", "https://evil.example.com")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"status\":\"Faltou\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

            restrictedMockMvc
                    .perform(
                            get("/api/cuidadores/stream")
                                    .header("Origin", "https://evil.example.com")
                                    .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

            assertEquals(0, restrictedCuidadorService.contarAssinantesStreamAtivos());

            // S18: Confirma que Carlos Rocha (id=3) permanece Presente apos tentativa bloqueada
            assertEquals(
                    "Presente", restrictedCuidadorService.listarCuidadores().get(2).getStatus());
        }
    }

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"escala.sse.heartbeat-ms=120", "escala.sse.max-subscribers=3"})
    @DisplayName(
            "Stream SSE real sobre TCP/HTTP (RANDOM_PORT): headers, broadcasts, LGPD, heartbeat e FIFO")
    class SseRealtimeStreamIntegrationTest {

        @LocalServerPort private int port;

        @Autowired private CuidadorService liveCuidadorService;

        @Autowired private JsonMapper liveJsonMapper;

        record SseFrame(String id, String event, String retry, String data) {}

        static final class LiveSseReader implements AutoCloseable {
            private final HttpResponse<InputStream> response;
            private final InputStream bodyStream;
            private final LinkedBlockingQueue<SseFrame> events = new LinkedBlockingQueue<>();
            private final LinkedBlockingQueue<String> comments = new LinkedBlockingQueue<>();
            private final CountDownLatch closedByServer = new CountDownLatch(1);
            private final AtomicInteger readErrors = new AtomicInteger(0);
            private final Thread readerThread;

            LiveSseReader(HttpResponse<InputStream> response) {
                this.response = response;
                this.bodyStream = response.body();
                this.readerThread =
                        new Thread(
                                this::consumeLoop,
                                "test-sse-reader-" + System.identityHashCode(this));
                this.readerThread.setDaemon(true);
                this.readerThread.start();
            }

            private void consumeLoop() {
                try (BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
                    String id = null;
                    String event = null;
                    String retry = null;
                    String data = null;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(":")) {
                            comments.offer(line);
                        } else if (line.startsWith("id:")) {
                            id = line.substring("id:".length()).trim();
                        } else if (line.startsWith("event:")) {
                            event = line.substring("event:".length()).trim();
                        } else if (line.startsWith("retry:")) {
                            retry = line.substring("retry:".length()).trim();
                        } else if (line.startsWith("data:")) {
                            data = line.substring("data:".length());
                        } else if (line.isEmpty() && data != null) {
                            events.offer(new SseFrame(id, event, retry, data));
                            id = null;
                            event = null;
                            retry = null;
                            data = null;
                        }
                    }
                    closedByServer.countDown();
                } catch (IOException ex) {
                    readErrors.incrementAndGet();
                    closedByServer.countDown();
                }
            }

            HttpResponse<InputStream> response() {
                return response;
            }

            SseFrame pollEvent(long timeout, TimeUnit unit) throws InterruptedException {
                return events.poll(timeout, unit);
            }

            String pollComment(long timeout, TimeUnit unit) throws InterruptedException {
                return comments.poll(timeout, unit);
            }

            boolean awaitServerClose(long timeout, TimeUnit unit) throws InterruptedException {
                return closedByServer.await(timeout, unit);
            }

            @Override
            public void close() {
                try {
                    bodyStream.close();
                } catch (IOException ex) {
                    readErrors.incrementAndGet();
                }
            }
        }

        @BeforeEach
        void restaurarEscalaLimpa() {
            liveCuidadorService.restaurarEscalaInicial();
        }

        private LiveSseReader abrirStream(
                HttpClient client, String querySuffix, String lastEventIdHeader) throws Exception {
            HttpRequest.Builder reqBuilder =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            "http://localhost:"
                                                    + port
                                                    + "/api/cuidadores/stream"
                                                    + querySuffix))
                            .GET()
                            .header("Origin", VITE_ORIGIN)
                            .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
            if (lastEventIdHeader != null) {
                reqBuilder.header("Last-Event-ID", lastEventIdHeader);
            }
            HttpResponse<InputStream> resp =
                    client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            return new LiveSseReader(resp);
        }

        @Test
        @DisplayName(
                "Transmite SCHEDULE_UPDATED em tempo real (PATCH, batch, reset) isolando ?privacy=true, ignorando falhas 400/404, emitindo :heartbeat e aplicando FIFO (S01..S16)")
        void deveTransmitirEventosEmTempoRealComIsolamentoLgpdHeartbeatEFifo() throws Exception {
            HttpClient httpClient =
                    HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1)
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();

            // S10: Last-Event-ID nao-numerico hostil jamais causa erro 500/NumberFormatException
            try (LiveSseReader leitorHostil = abrirStream(httpClient, "", "nao-numerico-xyz")) {
                assertEquals(200, leitorHostil.response().statusCode());
                SseFrame inicialHostil = leitorHostil.pollEvent(5, TimeUnit.SECONDS);
                assertNotNull(inicialHostil);
                assertEquals("SCHEDULE_UPDATED", inicialHostil.event());
            }

            try (LiveSseReader clienteGestor = abrirStream(httpClient, "", "999999");
                    LiveSseReader clienteMuralLgpd =
                            abrirStream(httpClient, "?privacy=true", null)) {

                // S02, M2.1, M6.1: Headers obrigatorios de stream UTF-8 + anti-buffering + CORS
                for (LiveSseReader cliente : List.of(clienteGestor, clienteMuralLgpd)) {
                    assertEquals(200, cliente.response().statusCode());
                    String contentType =
                            cliente.response().headers().firstValue("Content-Type").orElse("");
                    assertTrue(contentType.contains("text/event-stream"));
                    assertTrue(contentType.toLowerCase().contains("charset=utf-8"));
                    assertEquals(
                            "no-cache, no-transform",
                            cliente.response().headers().firstValue("Cache-Control").orElse(""));
                    assertEquals(
                            "no",
                            cliente.response()
                                    .headers()
                                    .firstValue("X-Accel-Buffering")
                                    .orElse(""));
                    assertEquals(
                            "*",
                            cliente.response()
                                    .headers()
                                    .firstValue("Access-Control-Allow-Origin")
                                    .orElse(""));
                }

                // S01, S03, S04, S10: Evento inicial imediato mesmo com Last-Event-ID: 999999
                SseFrame initGestor = clienteGestor.pollEvent(5, TimeUnit.SECONDS);
                SseFrame initMural = clienteMuralLgpd.pollEvent(5, TimeUnit.SECONDS);
                assertNotNull(initGestor);
                assertNotNull(initMural);
                assertEquals("SCHEDULE_UPDATED", initGestor.event());
                assertEquals("3000", initGestor.retry());
                assertTrue(initGestor.data().startsWith("[{"));
                assertFalse(initGestor.data().contains("null"));

                long seqInicial = Long.parseLong(initGestor.id());
                assertTrue(seqInicial >= 1L);

                List<Cuidador> listaInitGestor =
                        liveJsonMapper.readValue(
                                initGestor.data(), new TypeReference<List<Cuidador>>() {});
                List<Cuidador> listaInitMural =
                        liveJsonMapper.readValue(
                                initMural.data(), new TypeReference<List<Cuidador>>() {});
                assertEquals("Carlos Rocha", listaInitGestor.get(2).getNome());
                assertEquals("Carlos R.", listaInitMural.get(2).getNome());

                // S16: Heartbeat periodico (:heartbeat) mantem proxy vivo sem disparar evento
                String comentario = clienteGestor.pollComment(3, TimeUnit.SECONDS);
                assertNotNull(comentario);
                assertEquals(":heartbeat", comentario);

                // S06: Chamadas invalidas (404 e 400) jamais emitem SCHEDULE_UPDATED
                HttpResponse<String> resp404 =
                        httpClient.send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:"
                                                                + port
                                                                + "/api/cuidadores/999/status"))
                                        .method(
                                                "PATCH",
                                                HttpRequest.BodyPublishers.ofString(
                                                        "{\"status\":\"Faltou\"}"))
                                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                assertEquals(404, resp404.statusCode());

                HttpResponse<String> resp400 =
                        httpClient.send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:"
                                                                + port
                                                                + "/api/cuidadores/batch"))
                                        .POST(HttpRequest.BodyPublishers.ofString("[]"))
                                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                assertEquals(400, resp400.statusCode());

                // S05, S07, S08: Mutacao 1 -> POST /api/cuidadores/batch com acento e telefone
                String loteJson =
                        """
                        [
                          {
                            "id": 3,
                            "nome": "Carlos Rocha",
                            "status": "Cobertura",
                            "turno": "Noturno (19h-07h)",
                            "quarto": "Quarto 03",
                            "telefone": "+55 11 90000-0010",
                            "horasDescanso": 8,
                            "substituto": "João da Conceição"
                          }
                        ]
                        """;
                HttpResponse<String> respBatch =
                        httpClient.send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:"
                                                                + port
                                                                + "/api/cuidadores/batch"))
                                        .POST(HttpRequest.BodyPublishers.ofString(loteJson))
                                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                assertEquals(200, respBatch.statusCode());

                SseFrame evBatchGestor = clienteGestor.pollEvent(5, TimeUnit.SECONDS);
                SseFrame evBatchMural = clienteMuralLgpd.pollEvent(5, TimeUnit.SECONDS);
                assertNotNull(evBatchGestor);
                assertNotNull(evBatchMural);
                // Prova matematica de S06: id saltou exatamente +1 (nenhum evento espurio nas
                // falhas 404/400!)
                assertEquals(seqInicial + 1L, Long.parseLong(evBatchGestor.id()));
                assertEquals(seqInicial + 1L, Long.parseLong(evBatchMural.id()));

                List<Cuidador> dadosBatchGestor =
                        liveJsonMapper.readValue(
                                evBatchGestor.data(), new TypeReference<List<Cuidador>>() {});
                List<Cuidador> dadosBatchMural =
                        liveJsonMapper.readValue(
                                evBatchMural.data(), new TypeReference<List<Cuidador>>() {});

                assertEquals("Carlos Rocha", dadosBatchGestor.get(0).getNome());
                assertEquals("João da Conceição", dadosBatchGestor.get(0).getSubstituto());
                assertEquals("+55 11 90000-0010", dadosBatchGestor.get(0).getTelefone());

                assertEquals("Carlos R.", dadosBatchMural.get(0).getNome());
                assertEquals("João C.", dadosBatchMural.get(0).getSubstituto());
                assertEquals("(••) •••••-••••", dadosBatchMural.get(0).getTelefone());
                assertTrue(
                        dadosBatchMural
                                .get(0)
                                .getTelefone()
                                .chars()
                                .noneMatch(ch -> ch >= '0' && ch <= '9'));
                assertEquals(8, dadosBatchMural.get(0).getHorasDescanso());

                // Mutacao 2 -> PATCH /api/cuidadores/3/status
                HttpResponse<String> respPatch =
                        httpClient.send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:"
                                                                + port
                                                                + "/api/cuidadores/3/status"))
                                        .method(
                                                "PATCH",
                                                HttpRequest.BodyPublishers.ofString(
                                                        "{\"status\":\"Faltou\"}"))
                                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                assertEquals(200, respPatch.statusCode());

                SseFrame evPatchGestor = clienteGestor.pollEvent(5, TimeUnit.SECONDS);
                SseFrame evPatchMural = clienteMuralLgpd.pollEvent(5, TimeUnit.SECONDS);
                assertNotNull(evPatchGestor);
                assertNotNull(evPatchMural);
                assertEquals(seqInicial + 2L, Long.parseLong(evPatchGestor.id()));

                List<Cuidador> dadosPatchGestor =
                        liveJsonMapper.readValue(
                                evPatchGestor.data(), new TypeReference<List<Cuidador>>() {});
                assertEquals("Faltou", dadosPatchGestor.get(0).getStatus());
                assertNull(dadosPatchGestor.get(0).getSubstituto());

                // Mutacao 3 -> POST /api/cuidadores/reset
                HttpResponse<String> respReset =
                        httpClient.send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:"
                                                                + port
                                                                + "/api/cuidadores/reset"))
                                        .POST(HttpRequest.BodyPublishers.noBody())
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                assertEquals(200, respReset.statusCode());

                SseFrame evResetGestor = clienteGestor.pollEvent(5, TimeUnit.SECONDS);
                SseFrame evResetMural = clienteMuralLgpd.pollEvent(5, TimeUnit.SECONDS);
                assertNotNull(evResetGestor);
                assertNotNull(evResetMural);
                assertEquals(seqInicial + 3L, Long.parseLong(evResetGestor.id()));

                List<Cuidador> dadosResetMural =
                        liveJsonMapper.readValue(
                                evResetMural.data(), new TypeReference<List<Cuidador>>() {});
                assertEquals(4, dadosResetMural.size());
                assertEquals("Carlos R.", dadosResetMural.get(2).getNome());

                // S13: Eviccao FIFO ao exceder escala.sse.max-subscribers=3
                try (LiveSseReader cliente3 = abrirStream(httpClient, "", null);
                        LiveSseReader cliente4 = abrirStream(httpClient, "?privacy=true", null)) {
                    assertNotNull(cliente3.pollEvent(5, TimeUnit.SECONDS));
                    assertNotNull(cliente4.pollEvent(5, TimeUnit.SECONDS));
                    assertTrue(
                            clienteGestor.awaitServerClose(5, TimeUnit.SECONDS),
                            "Cliente mais antigo (FIFO) deveria ter sido encerrado ao abrir 4a conexao");
                    assertTrue(liveCuidadorService.contarAssinantesStreamAtivos() <= 3);
                }
            }
        }
    }
}
