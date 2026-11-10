package escala_elderly_api;

/** Excecao lancada quando um ID de cuidador valido nao existe na escala ativa (HTTP 404). */
public class CuidadorNotFoundException extends RuntimeException {

    public static final String CODE = "CUIDADOR_NOT_FOUND";

    private final Long cuidadorId;

    public CuidadorNotFoundException(Long cuidadorId) {
        super("Cuidador com id " + cuidadorId + " nao encontrado na escala ativa.");
        this.cuidadorId = cuidadorId;
    }

    public String getCode() {
        return CODE;
    }

    public Long getCuidadorId() {
        return cuidadorId;
    }
}
