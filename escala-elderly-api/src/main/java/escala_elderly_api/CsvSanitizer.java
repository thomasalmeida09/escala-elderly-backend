package escala_elderly_api;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Politica pura e deterministica de higienizacao contra OWASP CSV Formula Injection (CWE-1236) e
 * validacao gramatical simetrica de telefones nacionais/internacionais (ITU-T E.164).
 *
 * <p>Garante que nenhum texto persistido na memoria da escala comece com gatilhos executaveis em
 * planilhas (=, +, -, @, |, tabulacao, retorno de carro ou equivalentes Unicode fullwidth) nem
 * contenha quebras de linha internas capazes de injetar novas celulas durante a exportacao CSV.
 */
public final class CsvSanitizer {

    /** Limite bruto anti-DoS verificado em O(1) antes de normalizacao Unicode ou varreduras. */
    public static final int MAX_RAW_INPUT_LENGTH = 4096;

    public static final int MIN_DIGITOS_TELEFONE_BASE = 8;
    public static final int MAX_DIGITOS_TELEFONE_BASE = 15;

    private static final Set<String> PLACEHOLDERS_OPCIONAIS =
            Set.of("-", "--", "---", "—", "–", "n/a", "na", "s/n", "nenhum");

    private static final Pattern NOTACAO_CIENTIFICA_EXCEL_PATTERN =
            Pattern.compile("\\d[.,]\\d+e[+-]?\\d+", Pattern.CASE_INSENSITIVE);

    private static final Pattern SINTAXE_TELEFONE_ESTRITA_PATTERN =
            Pattern.compile(
                    "^(?:\\+[1-9]|\\([0-9]|[0-9])[0-9 ()./-]{6,31}(?:\\s*(?:ramal|ext\\.?|#)\\s*[0-9]{1,6})?$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern SUFIXO_RAMAL_PATTERN =
            Pattern.compile("(?:\\s*(?:ramal|ext\\.?|#)\\s*[0-9]{1,6})$", Pattern.CASE_INSENSITIVE);

    private CsvSanitizer() {}

    /**
     * Higieniza um campo textual obrigatorio (ex: nome), exigindo resultado nao-vazio dentro de
     * maxLen e livre de gatilhos iniciais de formula OWASP.
     */
    public static String higienizarTextoObrigatorio(String valor, String campo, int maxLen) {
        if (valor == null) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Campo obrigatorio '" + campo + "' nao pode ser nulo.");
        }

        String base = normalizarBaseUnicaLinhaNfc(valor, campo);
        String limpo = removerPrefixosPerigososTexto(base);

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

    /**
     * Higieniza um campo textual opcional (ex: turno, quarto, substituto), convertendo placeholders
     * de planilha ou entradas vazias apos sanitizacao em null.
     */
    public static String higienizarTextoOpcional(String valor, String campo, int maxLen) {
        if (valor == null) {
            return null;
        }

        String base = normalizarBaseUnicaLinhaNfc(valor, campo);
        if (base.isEmpty() || PLACEHOLDERS_OPCIONAIS.contains(base.toLowerCase(Locale.ROOT))) {
            return null;
        }

        String limpo = removerPrefixosPerigososTexto(base);
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

    /**
     * Higieniza e valida um telefone opcional em paridade simetrica com o Frontend: preserva o
     * prefixo internacional E.164 (+55...), aceita DDD entre parenteses e ramais opcionais,
     * converte placeholders em null e rejeita formulas, notacao cientifica ou mascaras sem digitos.
     */
    public static String higienizarTelefoneOpcional(String valor, String campo, int maxLen) {
        if (valor == null) {
            return null;
        }

        String base = normalizarBaseUnicaLinhaNfc(valor, campo);
        if (base.isEmpty() || PLACEHOLDERS_OPCIONAIS.contains(base.toLowerCase(Locale.ROOT))) {
            return null;
        }

        String limpo = removerPrefixosPerigososTelefone(base);
        if (limpo.isEmpty()) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Campo opcional '" + campo + "' contem expressao invalida sem digitos.");
        }
        if (limpo.length() > maxLen) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Campo opcional '" + campo + "' excede o limite de " + maxLen + " caracteres.");
        }
        if (NOTACAO_CIENTIFICA_EXCEL_PATTERN.matcher(limpo).find()) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Telefone corrompido por notacao cientifica em '" + campo + "'.");
        }
        if (!SINTAXE_TELEFONE_ESTRITA_PATTERN.matcher(limpo).matches()
                || !possuiParentesesBalanceadosComDigito(limpo)) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Formato de telefone invalido em '" + campo + "'.");
        }

        String numeroBase = SUFIXO_RAMAL_PATTERN.matcher(limpo).replaceFirst("");
        int digitosBase = contarDigitosAscii(numeroBase);
        if (digitosBase < MIN_DIGITOS_TELEFONE_BASE || digitosBase > MAX_DIGITOS_TELEFONE_BASE) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Quantidade de digitos invalida em '"
                            + campo
                            + "' (esperado entre "
                            + MIN_DIGITOS_TELEFONE_BASE
                            + " e "
                            + MAX_DIGITOS_TELEFONE_BASE
                            + " digitos no numero principal).");
        }

        return limpo;
    }

    /**
     * Valida caracteres nulos e teto bruto anti-DoS sem alterar o valor (util para campos
     * enumerados como status que jamais devem sofrer sanitizacao silenciosa).
     */
    public static void validarLimitesBrutosSemNulo(String valor, String campo) {
        if (valor.indexOf('\0') >= 0) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Campo '" + campo + "' contem caractere de controle nulo proibido.");
        }
        if (valor.length() > MAX_RAW_INPUT_LENGTH) {
            throw new InvalidCuidadorPayloadException(
                    InvalidCuidadorPayloadException.CODE_INVALID_PAYLOAD,
                    "Campo '"
                            + campo
                            + "' excede o limite bruto de seguranca de "
                            + MAX_RAW_INPUT_LENGTH
                            + " caracteres.");
        }
    }

    private static String normalizarBaseUnicaLinhaNfc(String valor, String campo) {
        validarLimitesBrutosSemNulo(valor, campo);

        StringBuilder filtrado = new StringBuilder(valor.length());
        for (int offset = 0; offset < valor.length(); ) {
            int cp = valor.codePointAt(offset);
            offset += Character.charCount(cp);

            if (ehSeparadorOuEspaco(cp)) {
                filtrado.append(' ');
            } else if (!ehInvisivelOuControleSuprimivel(cp)) {
                filtrado.appendCodePoint(cp);
            }
        }

        String nfc = Normalizer.normalize(filtrado.toString(), Normalizer.Form.NFC);
        return colapsarEspacosETripar(nfc);
    }

    private static String colapsarEspacosETripar(String texto) {
        StringBuilder resultado = new StringBuilder(texto.length());
        boolean ultimoFoiEspaco = true;

        for (int offset = 0; offset < texto.length(); ) {
            int cp = texto.codePointAt(offset);
            offset += Character.charCount(cp);

            if (ehSeparadorOuEspaco(cp)) {
                if (!ultimoFoiEspaco) {
                    resultado.append(' ');
                    ultimoFoiEspaco = true;
                }
            } else if (!ehInvisivelOuControleSuprimivel(cp)) {
                resultado.appendCodePoint(cp);
                ultimoFoiEspaco = false;
            }
        }

        int len = resultado.length();
        if (len > 0 && resultado.charAt(len - 1) == ' ') {
            resultado.setLength(len - 1);
        }
        return resultado.toString();
    }

    private static String removerPrefixosPerigososTexto(String texto) {
        int idx = 0;
        while (idx < texto.length()) {
            int cp = texto.codePointAt(idx);
            if (ehGatilhoInicialTexto(cp)) {
                idx += Character.charCount(cp);
            } else {
                break;
            }
        }
        return texto.substring(idx).trim();
    }

    private static String removerPrefixosPerigososTelefone(String texto) {
        int idx = 0;
        while (idx < texto.length()) {
            int cp = texto.codePointAt(idx);
            if (cp == '+') {
                if (idx + 1 < texto.length()) {
                    char proximo = texto.charAt(idx + 1);
                    if (proximo >= '1' && proximo <= '9') {
                        break;
                    }
                }
                idx++;
            } else if (ehGatilhoInicialTexto(cp)) {
                idx += Character.charCount(cp);
            } else {
                break;
            }
        }
        return texto.substring(idx).trim();
    }

    private static boolean ehSeparadorOuEspaco(int cp) {
        return cp == '\t'
                || cp == '\n'
                || cp == '\u000B'
                || cp == '\f'
                || cp == '\r'
                || cp == '\u0085'
                || cp == '\u00A0'
                || cp == '\u2028'
                || cp == '\u2029'
                || Character.isSpaceChar(cp)
                || Character.isWhitespace(cp);
    }

    private static boolean ehInvisivelOuControleSuprimivel(int cp) {
        return cp < 0x20
                || (cp >= 0x7F && cp <= 0x9F)
                || cp == 0x00AD
                || cp == 0x034F
                || cp == 0x061C
                || cp == 0x180E
                || (cp >= 0x200B && cp <= 0x200F)
                || (cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2060 && cp <= 0x206F)
                || cp == 0xFEFF;
    }

    private static boolean ehGatilhoInicialTexto(int cp) {
        return cp == ' '
                || cp == '\''
                || cp == '\uFF07'
                || cp == '='
                || cp == '\uFF1D'
                || cp == '+'
                || cp == '\uFF0B'
                || cp == '-'
                || cp == '\uFF0D'
                || cp == '\u2212'
                || cp == '\u2013'
                || cp == '\u2014'
                || cp == '@'
                || cp == '\uFF20'
                || cp == '|'
                || cp == '\uFF5C';
    }

    private static boolean possuiParentesesBalanceadosComDigito(String telefone) {
        boolean dentroDeParenteses = false;
        int digitosNoGrupoAtual = 0;

        for (int i = 0; i < telefone.length(); i++) {
            char c = telefone.charAt(i);
            if (c == '(') {
                if (dentroDeParenteses) {
                    return false;
                }
                dentroDeParenteses = true;
                digitosNoGrupoAtual = 0;
            } else if (c == ')') {
                if (!dentroDeParenteses || digitosNoGrupoAtual == 0) {
                    return false;
                }
                dentroDeParenteses = false;
            } else if (dentroDeParenteses && c >= '0' && c <= '9') {
                digitosNoGrupoAtual++;
            }
        }
        return !dentroDeParenteses;
    }

    private static int contarDigitosAscii(String texto) {
        int contagem = 0;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c >= '0' && c <= '9') {
                contagem++;
            }
        }
        return contagem;
    }
}
