package escala_elderly_api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 1. Avisa o Spring que este arquivo responde chamadas HTTP em JSON e streams SSE.
// 2. A politica de CORS para /api/** e centralizada no CorsFilter de WebApiConfig via
//    application.properties (escala.cors.allowed-origins), evitando @CrossOrigin fixo no codigo.
@RestController
public class CuidadorController {

    private final CuidadorService cuidadorService;

    // 3. Injecao de dependencia pelo construtor: delega o estado ao servico thread-safe
    public CuidadorController(CuidadorService cuidadorService) {
        this.cuidadorService = cuidadorService;
    }

    // 4. Lista todos os cuidadores alinhados ao Frontend (suporta ?privacy=true para Mural LGPD)
    @GetMapping("/api/cuidadores")
    public List<Cuidador> listarCuidadores(HttpServletRequest request) {
        return cuidadorService.listarCuidadores(extrairModoPrivacidade(request));
    }

    // 5. Abre stream unidirecional em tempo real via Server-Sent Events (SSE) enviando o snapshot
    //    inicial imediato e notificando mutacoes com isolamento LGPD (?privacy=true) por cliente.
    @GetMapping(value = "/api/cuidadores/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter conectarStreamTempoReal(
            HttpServletRequest request, HttpServletResponse response) {
        boolean modoPrivacidade = extrairModoPrivacidade(request);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_TYPE, "text/event-stream;charset=UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return cuidadorService.registrarAssinanteStream(modoPrivacidade);
    }

    // 6. Atualiza o status de um cuidador especifico (ex: "Faltou", "Presente", "Cobertura")
    @PatchMapping("/api/cuidadores/{id}/status")
    public Cuidador atualizarStatus(
            @PathVariable("id") Long id, @RequestBody StatusUpdateRequest request) {
        return cuidadorService.atualizarStatus(id, request);
    }

    // 7. Sincroniza em lote uma lista de cuidadores importada via planilha CSV no Frontend
    @PostMapping("/api/cuidadores/batch")
    public List<Cuidador> sincronizarLote(@RequestBody List<CuidadorBatchItem> itens) {
        return cuidadorService.sincronizarLote(itens);
    }

    // 8. Restaura a escala inicial de demonstracao com 1 clique
    @PostMapping("/api/cuidadores/reset")
    public List<Cuidador> restaurarEscalaInicial() {
        return cuidadorService.restaurarEscalaInicial();
    }

    private static boolean extrairModoPrivacidade(HttpServletRequest request) {
        String[] valoresBrutos = request.getParameterValues("privacy");
        return LgpdPrivacyMasker.validarParametroPrivacy(
                valoresBrutos == null ? null : Arrays.asList(valoresBrutos));
    }
}
