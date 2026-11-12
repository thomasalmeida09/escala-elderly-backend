package escala_elderly_api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Tratador global de excecoes da API REST: traduz erros de validacao, rota, metodo e JSON em um
 * formato padronizado sem vazar stack traces internos.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CuidadorNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> tratarCuidadorNaoEncontrado(
            CuidadorNotFoundException ex, HttpServletRequest request) {
        return construirResposta(
                HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> tratarRotaNaoEncontrada(
            NoResourceFoundException ex, HttpServletRequest request) {
        return construirResposta(
                HttpStatus.NOT_FOUND,
                "ENDPOINT_NOT_FOUND",
                "Rota nao encontrada na API.",
                request.getRequestURI());
    }

    @ExceptionHandler(InvalidCuidadorPayloadException.class)
    public ResponseEntity<ApiErrorResponse> tratarPayloadInvalido(
            InvalidCuidadorPayloadException ex, HttpServletRequest request) {
        return construirResposta(
                HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> tratarTipoDeParametroInvalido(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        boolean ehParametroId = "id".equals(ex.getName());
        String codigo =
                ehParametroId
                        ? InvalidCuidadorPayloadException.CODE_INVALID_ID
                        : InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD;
        String mensagem =
                ehParametroId
                        ? "Parametro de rota invalido: informe um identificador numerico inteiro positivo."
                        : "Parametro '" + ex.getName() + "' possui tipo ou formato invalido.";
        return construirResposta(HttpStatus.BAD_REQUEST, codigo, mensagem, request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> tratarJsonMalformado(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return construirResposta(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON",
                "Corpo JSON ausente, malformado ou com tipos de dados incompativeis.",
                request.getRequestURI());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> tratarMetodoNaoSuportado(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return construirResposta(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "Metodo HTTP nao suportado para esta rota.",
                request.getRequestURI());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> tratarMediaTypeNaoSuportado(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return construirResposta(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type nao suportado. Utilize application/json.",
                request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> tratarErroInternoInesperado(
            Exception ex, HttpServletRequest request) {
        return construirResposta(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Erro interno inesperado ao processar a requisicao.",
                request.getRequestURI());
    }

    private static ResponseEntity<ApiErrorResponse> construirResposta(
            HttpStatus status, String code, String message, String path) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, path, Instant.now()));
    }
}
