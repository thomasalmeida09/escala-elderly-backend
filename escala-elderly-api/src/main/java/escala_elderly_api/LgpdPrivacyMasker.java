package escala_elderly_api;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Politica pura de minimizacao e pseudonimizacao read-time conforme a LGPD (Lei 13.709/2018, Art.
 * 6o III/VII e Art. 13) para exibicao segura em paineis compartilhados de enfermagem.
 *
 * <p>Opera exclusivamente sobre novas instancias imutaveis sem jamais alterar o snapshot canonico
 * mantido em memoria pelo CuidadorService.
 */
public final class LgpdPrivacyMasker {

    public static final String MASCARA_TELEFONE_LGPD = "(••) •••••-••••";

    public static final Set<String> PARTICULAS_NOME_PT_BR =
            Set.of("da", "de", "do", "das", "dos", "e");

    private LgpdPrivacyMasker() {}

    /**
     * Valida de forma estrita o parametro de query 'privacy', bloqueando coercao booleana frouxa e
     * HTTP Parameter Pollution (?privacy=false&privacy=true).
     */
    public static boolean validarParametroPrivacy(List<String> valores) {
        if (valores == null || valores.isEmpty()) {
            return false;
        }
        if (valores.size() > 1) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Parametro 'privacy' nao pode ser informado multiplas vezes.");
        }

        String bruto = valores.get(0);
        if (bruto == null) {
            return false;
        }
        if (bruto.indexOf('\0') >= 0) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Parametro 'privacy' contem caractere de controle nulo proibido.");
        }

        String limpo = bruto.trim();
        if ("true".equalsIgnoreCase(limpo)) {
            return true;
        }
        if ("false".equalsIgnoreCase(limpo)) {
            return false;
        }

        throw new InvalidCuidadorPayloadException(
                InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                "Parametro 'privacy' invalido: utilize true ou false.");
    }

    /**
     * Pseudonimiza nomes civis em paridade 1:1 com maskPersonNameLgpd do Frontend:
     *
     * <ul>
     *   <li>"Carlos Rocha" -> "Carlos R."
     *   <li>"João da Conceição" -> "João C." (ignora preposicoes PT-BR da/de/do/das/dos/e)
     *   <li>"João da" -> "João D." (fallback seguro quando ha apenas particulas apos o prenome)
     *   <li>Normaliza entradas Unicode NFD para NFC antes de extrair a inicial acentuada.
     * </ul>
     */
    public static String mascararNomePessoa(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) {
            return "";
        }

        String normalizado = Normalizer.normalize(nomeCompleto, Normalizer.Form.NFC).trim();
        if (normalizado.isEmpty()) {
            return "";
        }

        String[] tokens = normalizado.split("\\s+");
        if (tokens.length <= 1) {
            return tokens[0];
        }

        String primeiroNome = tokens[0];
        String sobrenomeAlvo = null;
        for (int i = 1; i < tokens.length; i++) {
            if (!PARTICULAS_NOME_PT_BR.contains(tokens[i].toLowerCase(Locale.ROOT))) {
                sobrenomeAlvo = tokens[i];
                break;
            }
        }
        if (sobrenomeAlvo == null) {
            sobrenomeAlvo = tokens[tokens.length - 1];
        }

        int inicialCodePoint = Character.toUpperCase(sobrenomeAlvo.codePointAt(0));
        String inicial = new String(Character.toChars(inicialCodePoint));
        return primeiroNome + " " + inicial + ".";
    }

    /** Mascara telefones nao-nulos com zero digitos expostos; preserva null para @JsonInclude. */
    public static String mascararTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return null;
        }
        return MASCARA_TELEFONE_LGPD;
    }

    /**
     * Projeta uma nova instancia imutavel de Cuidador com nome, substituto e telefone mascarados,
     * preservando id, status, turno, quarto e horasDescanso (seguranca interjornada Art. 66 CLT).
     */
    public static Cuidador projetarCuidadorPublico(Cuidador original) {
        Objects.requireNonNull(original, "Cuidador original nao pode ser nulo.");

        String nomeMascarado = mascararNomePessoa(original.getNome());
        String substitutoOriginal = original.getSubstituto();
        String substitutoMascarado =
                (substitutoOriginal == null || substitutoOriginal.isBlank())
                        ? null
                        : mascararNomePessoa(substitutoOriginal);
        String telefoneMascarado = mascararTelefone(original.getTelefone());

        return new Cuidador(
                original.getId(),
                nomeMascarado,
                original.getStatus(),
                original.getTurno(),
                original.getQuarto(),
                telefoneMascarado,
                original.getHorasDescanso(),
                substitutoMascarado);
    }

    /** Projeta uma lista imutavel completa para exposicao sob ?privacy=true sem tocar na origem. */
    public static List<Cuidador> projetarListaPublica(List<Cuidador> escala) {
        if (escala == null || escala.isEmpty()) {
            return List.of();
        }
        List<Cuidador> projetados = new ArrayList<>(escala.size());
        for (Cuidador cuidador : escala) {
            projetados.add(projetarCuidadorPublico(cuidador));
        }
        return List.copyOf(projetados);
    }
}
