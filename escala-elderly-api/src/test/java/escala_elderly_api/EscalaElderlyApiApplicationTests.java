package escala_elderly_api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("Contexto Spring Boot carrega sem erros")
    void contextLoads() {}

    @Test
    @DisplayName("GET /api/cuidadores retorna 200 OK, JSON valido V1.0 e cabecalho CORS")
    void deveListarCuidadoresComContratoV1ECorsAtivo() throws Exception {
        mockMvc.perform(get("/api/cuidadores").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("Ana Silva"))
                .andExpect(jsonPath("$[0].status").value("Trabalhando"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].nome").value("Carlos Eduardo"))
                .andExpect(jsonPath("$[2].id").value(3))
                .andExpect(jsonPath("$[2].nome").value("Mariana Costa"));
    }

    @Test
    @DisplayName("OPTIONS /api/cuidadores responde preflight CORS para origem Vite localhost:5173")
    void deveResponderPreflightCorsParaFrontendVite() throws Exception {
        mockMvc.perform(
                        options("/api/cuidadores")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }
}
