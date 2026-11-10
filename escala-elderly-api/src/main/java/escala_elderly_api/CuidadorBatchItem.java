package escala_elderly_api;

/**
 * Item individual do lote recebido em POST /api/cuidadores/batch (ex: importacao de planilha CSV).
 */
public record CuidadorBatchItem(
        Long id,
        String nome,
        String status,
        String turno,
        String quarto,
        String telefone,
        Integer horasDescanso,
        String substituto) {}
