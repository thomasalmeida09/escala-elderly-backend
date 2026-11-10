package escala_elderly_api;

import java.util.Set;

/**
 * Politica canonica de dominio para os estados validos de um cuidador na escala (ADR-BE-010).
 * Centraliza os textos permitidos em um unico ponto para evitar strings espalhadas (DRY).
 */
public final class StatusCuidador {

    public static final String PRESENTE = "Presente";
    public static final String FALTOU = "Faltou";
    public static final String FOLGA = "Folga";
    public static final String COBERTURA = "Cobertura";

    public static final Set<String> VALORES_PERMITIDOS = Set.of(PRESENTE, FALTOU, FOLGA, COBERTURA);

    private StatusCuidador() {}

    public static boolean ehValido(String status) {
        return status != null && VALORES_PERMITIDOS.contains(status);
    }
}
