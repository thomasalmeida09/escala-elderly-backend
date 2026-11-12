package escala_elderly_api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Tratador global de excecoes da API REST e streams SSE: traduz erros de validacao, rota, metodo e
 * JSON em formato padronizado application/json sem vazar stack traces internos e sem conflitar com
 * respostas text/event-stream ja comitadas.
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

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> tratarMediaTypeNaoAceitavel(
            HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return construirResposta(
                HttpStatus.NOT_ACCEPTABLE,
                "NOT_ACCEPTABLE",
                "Formato de resposta solicitado em Accept nao e suportado por esta rota.",
                request.getRequestURI());
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> tratarConexaoAssincronaEncerradaPeloCliente(
            AsyncRequestNotUsableException ex) {
        return null;
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> tratarTimeoutAssincrono(
            AsyncRequestTimeoutException ex, HttpServletResponse response) {
        if (response != null && response.isCommitted()) {
            return null;
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> tratarErroInternoInesperado(
            Exception ex, HttpServletRequest request, HttpServletResponse response) {
        if (ehRespostaSseOuJaComitada(response, ex)) {
            return null;
        }
        return construirResposta(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Erro interno inesperado ao processar a requisicao.",
                request.getRequestURI());
    }

    public ResponseEntity<ApiErrorResponse> tratarErroInternoInesperado(
            Exception ex, HttpServletRequest request) {
        return tratarErroInternoInesperado(ex, request, null);
    }

    private static boolean ehRespostaSseOuJaComitada(HttpServletResponse response, Exception ex) {
        if (response == null) {
            return false;
        }
        if (response.isCommitted()) {
            return true;
        }
        String contentType = response.getContentType();
        return ex instanceof IOException
                && contentType != null
                && contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private static ResponseEntity<ApiErrorResponse> construirResposta(
            HttpStatus status, String code, String message, String path) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(code, message, path, Instant.now()));
    }
}
