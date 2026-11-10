package escala_elderly_api;

/** Excecao de dominio lancada quando o chamador viola uma pre-condicao de contrato (HTTP 400). */
public class InvalidCuidadorPayloadException extends RuntimeException {

    public static final String CODE_INVALID_PAYLOAD = "INVALID_PAYLOAD";
    public static final String CODE_INVALID_STATUS = "INVALID_STATUS";
    public static final String CODE_INVALID_ID = "INVALID_ID";

    private final String code;

    public InvalidCuidadorPayloadException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
