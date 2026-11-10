package escala_elderly_api;

/**
 * Payload de entrada para atualizacao pontual de status via PATCH /api/cuidadores/{id}/status.
 *
 * @param status Novo status canonico ("Presente", "Faltou", "Folga" ou "Cobertura")
 * @param substituto Nome opcional do folguista alocado quando status for "Cobertura"
 */
public record StatusUpdateRequest(String status, String substituto) {}
