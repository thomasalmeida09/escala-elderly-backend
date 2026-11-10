package escala_elderly_api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
                .andExpect(jsonPath("$.substituto").doesNotExist());

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
                .andExpect(jsonPath("$.substituto").doesNotExist());
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
                .andExpect(jsonPath("$[1].nome").value("Folguista Turno B"));

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
                .andExpect(jsonPath("$[2].status").value("Presente"));
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

        // 400 MALFORMED_JSON para JSON truncado, literal null e coercao escalar indevida (M1)
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

        // 405, 415 e 404 de rota nao mapeada com CORS garantido em /api/** (M3)
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
    }
}
