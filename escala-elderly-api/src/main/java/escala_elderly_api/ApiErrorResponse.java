package escala_elderly_api;

import java.time.Instant;

/**
 * Envelope uniforme de erro HTTP para diagnostico deterministico pelo cliente (Frontend / cURL).
 */
public record ApiErrorResponse(String code, String message, String path, Instant timestamp) {}
