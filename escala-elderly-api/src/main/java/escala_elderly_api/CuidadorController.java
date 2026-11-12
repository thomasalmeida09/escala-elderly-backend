package escala_elderly_api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// 1. Avisa o Spring que este arquivo responde chamadas HTTP em JSON.
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
        String[] valoresBrutos = request.getParameterValues("privacy");
        boolean modoPrivacidade =
                LgpdPrivacyMasker.validarParametroPrivacy(
                        valoresBrutos == null ? null : Arrays.asList(valoresBrutos));
        return cuidadorService.listarCuidadores(modoPrivacidade);
    }

    // 5. Atualiza o status de um cuidador especifico (ex: "Faltou", "Presente", "Cobertura")
    @PatchMapping("/api/cuidadores/{id}/status")
    public Cuidador atualizarStatus(
            @PathVariable("id") Long id, @RequestBody StatusUpdateRequest request) {
        return cuidadorService.atualizarStatus(id, request);
    }

    // 6. Sincroniza em lote uma lista de cuidadores importada via planilha CSV no Frontend
    @PostMapping("/api/cuidadores/batch")
    public List<Cuidador> sincronizarLote(@RequestBody List<CuidadorBatchItem> itens) {
        return cuidadorService.sincronizarLote(itens);
    }

    // 7. Restaura a escala inicial de demonstracao com 1 clique
    @PostMapping("/api/cuidadores/reset")
    public List<Cuidador> restaurarEscalaInicial() {
        return cuidadorService.restaurarEscalaInicial();
    }
}
