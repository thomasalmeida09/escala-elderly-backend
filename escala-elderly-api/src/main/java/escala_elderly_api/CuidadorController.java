package escala_elderly_api;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.List;

@RestController // 1. Avisa o Spring que este arquivo vai responder chamadas de internet
@CrossOrigin(origins = "*") // 2. Libera o acesso para o React
public class CuidadorController {

    // 3. Quando alguém entrar no endereço "/api/cuidadores", execute este método:
    @GetMapping("/api/cuidadores")
    public List<Cuidador> listarCuidadores() {

        // 1. Cria uma caixa (lista) vazia
        List<Cuidador> lista = new ArrayList<>();

        // 2. Fabrica cada cuidador separado com nomes bem claros
        Cuidador c1 = new Cuidador(1L, "Ana Silva", "Trabalhando");
        Cuidador c2 = new Cuidador(2L, "Carlos Eduardo", "Trabalhando");
        Cuidador c3 = new Cuidador(3L, "Mariana Costa", "Trabalhando");

        // 3. Coloca um por um dentro da caixa
        lista.add(c1);
        lista.add(c2);
        lista.add(c3);

        // 4. Entrega a caixa cheia
        return lista;
    }
}
