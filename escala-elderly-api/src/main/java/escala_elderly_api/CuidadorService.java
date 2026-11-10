package escala_elderly_api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/**
 * Servico responsavel por guardar e atualizar o estado da escala em memoria de forma thread-safe
 * (ADR-BE-010).
 *
 * <p>Utiliza um snapshot imutavel (AtomicReference<List<Cuidador>>):
 *
 * <ul>
 *   <li>Leituras (GET) ocorrem em O(1) sem travas (lock-free) e nunca enxergam listas vazias ou
 *       lotes pela metade (Zero Torn Reads).
 *   <li>Escritas (PATCH, POST /batch, POST /reset) validam 100% dos dados antes de trocar a
 *       referencia atomicamente sob writeLock (Crash Early / Tudo-ou-Nada).
 * </ul>
 */
@Service
public class CuidadorService {

    /** Limite seguro de inteiros IEEE-754 (Number.MAX_SAFE_INTEGER no JavaScript/TypeScript). */
    public static final long MAX_SAFE_JS_ID = 9_007_199_254_740_991L;

    public static final int MAX_BATCH_SIZE = 500;
    public static final int MAX_TEXT_LENGTH = 120;
    public static final int MAX_PHONE_LENGTH = 32;
    public static final int MAX_REST_HOURS = 720;

    private final Object writeLock = new Object();
    private final AtomicReference<List<Cuidador>> snapshotRef =
            new AtomicReference<>(criarEscalaInicialPadrao());

    /** Retorna o snapshot imutavel atual ordenado por ID crescente em O(1). */
    public List<Cuidador> listarCuidadores() {
        return snapshotRef.get();
    }

    /**
     * Atualiza o status (e opcionalmente o substituto) de um cuidador especifico. Valida todas as
     * pre-condicoes do chamador (400) antes de buscar o ID na escala (404).
     */
    public Cuidador atualizarStatus(Long id, StatusUpdateRequest request) {
        validarId(id, "id");
        if (request == null) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Corpo da requisicao de atualizacao de status nao pode ser nulo.");
        }

        String novoStatus = normalizarStatusObrigatorio(request.status(), "status");
        String novoSubstituto =
                normalizarSubstitutoPorStatus(novoStatus, request.substituto(), "substituto");

        synchronized (writeLock) {
            List<Cuidador> atual = snapshotRef.get();
            List<Cuidador> proximo = new ArrayList<>(atual.size());
            Cuidador atualizado = null;

            for (Cuidador item : atual) {
                if (item.getId().equals(id)) {
                    atualizado =
                            new Cuidador(
                                    item.getId(),
                                    item.getNome(),
                                    novoStatus,
                                    item.getTurno(),
                                    item.getQuarto(),
                                    item.getTelefone(),
                                    item.getHorasDescanso(),
                                    novoSubstituto);
                    proximo.add(atualizado);
                } else {
                    proximo.add(item);
                }
            }

            if (atualizado == null) {
                throw new CuidadorNotFoundException(id);
            }

            snapshotRef.set(List.copyOf(proximo));
            return atualizado;
        }
    }

    /**
     * Substitui atomicamente a escala ativa por um lote validado (ex: importacao CSV). Se qualquer
     * item falhar na validacao, nenhum registro da escala anterior e alterado.
     */
    public List<Cuidador> sincronizarLote(List<CuidadorBatchItem> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "O lote de cuidadores nao pode ser nulo nem vazio.");
        }
        if (itens.size() > MAX_BATCH_SIZE) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "O lote excede o limite maximo permitido de "
                            + MAX_BATCH_SIZE
                            + " cuidadores.");
        }

        Set<Long> idsExplicitos = new HashSet<>();
        long maxExplicitId = 0L;
        int quantidadeSemId = 0;

        for (int i = 0; i < itens.size(); i++) {
            CuidadorBatchItem item = itens.get(i);
            String prefixo = "item[" + i + "]";
            if (item == null) {
                throw new InvalidCuidadorPayloadException(
                        InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                        prefixo + " nao pode ser nulo.");
            }
            if (item.id() != null) {
                validarId(item.id(), prefixo + ".id");
                if (!idsExplicitos.add(item.id())) {
                    throw new InvalidCuidadorPayloadException(
                            InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                            "ID duplicado no lote: " + item.id() + " em " + prefixo + ".");
                }
                if (item.id() > maxExplicitId) {
                    maxExplicitId = item.id();
                }
            } else {
                quantidadeSemId++;
            }
        }

        if (maxExplicitId > MAX_SAFE_JS_ID - quantidadeSemId) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_ID,
                    "Geracao automatica de ID ultrapassaria o limite seguro MAX_SAFE_JS_ID.");
        }

        long proximoAutoId = maxExplicitId + 1L;
        List<Cuidador> validados = new ArrayList<>(itens.size());

        for (int i = 0; i < itens.size(); i++) {
            CuidadorBatchItem item = itens.get(i);
            String prefixo = "item[" + i + "]";

            long idFinal = (item.id() != null) ? item.id() : proximoAutoId++;
            String nome =
                    normalizarTextoObrigatorio(item.nome(), prefixo + ".nome", MAX_TEXT_LENGTH);
            String status = normalizarStatusObrigatorio(item.status(), prefixo + ".status");
            String turno =
                    normalizarTextoOpcional(item.turno(), prefixo + ".turno", MAX_TEXT_LENGTH);
            String quarto =
                    normalizarTextoOpcional(item.quarto(), prefixo + ".quarto", MAX_TEXT_LENGTH);
            String telefone =
                    normalizarTextoOpcional(
                            item.telefone(), prefixo + ".telefone", MAX_PHONE_LENGTH);
            validarHorasDescanso(item.horasDescanso(), prefixo + ".horasDescanso");
            String substituto =
                    normalizarSubstitutoPorStatus(
                            status, item.substituto(), prefixo + ".substituto");

            validados.add(
                    new Cuidador(
                            idFinal,
                            nome,
                            status,
                            turno,
                            quarto,
                            telefone,
                            item.horasDescanso(),
                            substituto));
        }

        validados.sort(Comparator.comparingLong(Cuidador::getId));
        List<Cuidador> novoSnapshot = List.copyOf(validados);

        synchronized (writeLock) {
            snapshotRef.set(novoSnapshot);
            return novoSnapshot;
        }
    }

    /** Restaura atomicamente os 4 cuidadores canonicos iniciais de demonstracao (ADR-BE-010). */
    public List<Cuidador> restaurarEscalaInicial() {
        List<Cuidador> inicial = criarEscalaInicialPadrao();
        synchronized (writeLock) {
            snapshotRef.set(inicial);
            return inicial;
        }
    }

    private static List<Cuidador> criarEscalaInicialPadrao() {
        return List.of(
                new Cuidador(
                        1L, "Ana Souza", StatusCuidador.PRESENTE, "Diurno (07h-19h)", "Quarto 01"),
                new Cuidador(
                        2L, "Lucas Lima", StatusCuidador.PRESENTE, "Diurno (07h-19h)", "Quarto 02"),
                new Cuidador(
                        3L,
                        "Carlos Rocha",
                        StatusCuidador.PRESENTE,
                        "Noturno (19h-07h)",
                        "Quarto 03"),
                new Cuidador(
                        4L,
                        "Mariana Costa",
                        StatusCuidador.PRESENTE,
                        "Noturno (19h-07h)",
                        "Quarto 04"));
    }

    private static void validarId(Long id, String campo) {
        if (id == null || id < 1L || id > MAX_SAFE_JS_ID) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_ID,
                    "Campo '"
                            + campo
                            + "' deve ser um inteiro positivo entre 1 e "
                            + MAX_SAFE_JS_ID
                            + ".");
        }
    }

    private static String normalizarStatusObrigatorio(String valor, String campo) {
        if (valor == null) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_STATUS,
                    "Campo '"
                            + campo
                            + "' e obrigatorio e deve ser um dentre: Presente, Faltou, Folga, Cobertura.");
        }
        rejeitarCaractereNulo(valor, campo);
        String limpo = valor.trim();
        if (!StatusCuidador.ehValido(limpo)) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_STATUS,
                    "Status invalido em '"
                            + campo
                            + "'. Valores permitidos: Presente, Faltou, Folga, Cobertura.");
        }
        return limpo;
    }

    private static String normalizarTextoObrigatorio(String valor, String campo, int maxLen) {
        if (valor == null) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Campo obrigatorio '" + campo + "' nao pode ser nulo.");
        }
        rejeitarCaractereNulo(valor, campo);
        String limpo = valor.trim();
        if (limpo.isEmpty() || limpo.length() > maxLen) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Campo '"
                            + campo
                            + "' deve conter entre 1 e "
                            + maxLen
                            + " caracteres validos.");
        }
        return limpo;
    }

    private static String normalizarTextoOpcional(String valor, String campo, int maxLen) {
        if (valor == null) {
            return null;
        }
        rejeitarCaractereNulo(valor, campo);
        String limpo = valor.trim();
        if (limpo.isEmpty()) {
            return null;
        }
        if (limpo.length() > maxLen) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Campo opcional '" + campo + "' excede o limite de " + maxLen + " caracteres.");
        }
        return limpo;
    }

    private static String normalizarSubstitutoPorStatus(
            String statusCanonica, String substitutoBruto, String campo) {
        String normalizado = normalizarTextoOpcional(substitutoBruto, campo, MAX_TEXT_LENGTH);
        return StatusCuidador.COBERTURA.equals(statusCanonica) ? normalizado : null;
    }

    private static void validarHorasDescanso(Integer horas, String campo) {
        if (horas != null && (horas < 0 || horas > MAX_REST_HOURS)) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Campo '" + campo + "' deve estar entre 0 e " + MAX_REST_HOURS + " horas.");
        }
    }

    private static void rejeitarCaractereNulo(String valor, String campo) {
        if (valor.indexOf('\0') >= 0) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Campo '" + campo + "' contem caractere de controle nulo proibido.");
        }
    }
}
