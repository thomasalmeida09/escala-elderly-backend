package escala_elderly_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecurityAndLgpdPolicyTest {

    @Test
    @DisplayName(
            "CsvSanitizer neutraliza prefixos OWASP encadeados, invisiveis Unicode e homografos fullwidth em ponto fixo (S01, S02)")
    void deveNeutralizarPrefixosOwaspEncadeadosInvisiveisEFullwidthEmPontoFixo() {
        assertEquals(
                "CMD|' /C calc'!A0",
                CsvSanitizer.higienizarTextoObrigatorio("=CMD|' /C calc'!A0", "nome", 120));
        assertEquals("CMD()", CsvSanitizer.higienizarTextoObrigatorio(" =+=-@|CMD()", "nome", 120));
        assertEquals("1+1", CsvSanitizer.higienizarTextoObrigatorio("\uFEFF=1+1", "nome", 120));
        assertEquals(
                "SUM(1,2)",
                CsvSanitizer.higienizarTextoObrigatorio("\u200B\u200E@SUM(1,2)", "nome", 120));
        assertEquals("CMD()", CsvSanitizer.higienizarTextoObrigatorio("＝＋－−＠｜'CMD()", "nome", 120));
        assertEquals(
                "CMD()",
                CsvSanitizer.higienizarTextoObrigatorio("=\u200B+\uFEFF-@CMD()", "nome", 120));
        assertEquals(
                "Folguista Turno A",
                CsvSanitizer.higienizarTextoOpcional(
                        "\t\r\n '=+Folguista Turno A  ", "substituto", 120));
    }

    @Test
    @DisplayName(
            "CsvSanitizer converte quebras de linha e separadores Unicode (NEL/LS/PS) em espaco unico sem colar palavras (S03)")
    void deveConverterQuebrasDeLinhaESeparadoresUnicodeEmEspacoUnico() {
        assertEquals(
                "Carlos =CMD()",
                CsvSanitizer.higienizarTextoObrigatorio("Carlos\r\n=CMD()", "nome", 120));
        assertEquals(
                "Ana Souza",
                CsvSanitizer.higienizarTextoObrigatorio("Ana\u0085Souza", "nome", 120));
        assertEquals(
                "Lucas Lima",
                CsvSanitizer.higienizarTextoObrigatorio("Lucas\u2028\u2029Lima", "nome", 120));
        assertEquals(
                "Quarto 03",
                CsvSanitizer.higienizarTextoOpcional("Quarto\t\n\u00A003", "quarto", 120));

        String resultado =
                CsvSanitizer.higienizarTextoObrigatorio("Maria\r\n\t\u0085Costa", "nome", 120);
        assertFalse(resultado.contains("\r"));
        assertFalse(resultado.contains("\n"));
        assertFalse(resultado.contains("\t"));
        assertFalse(resultado.contains("\u0085"));
        assertEquals("Maria Costa", resultado);
    }

    @Test
    @DisplayName(
            "CsvSanitizer rejeita campos obrigatorios esvaziados, bytes nulos e megastrings anti-DoS, convertendo opcionais vazios em null (S05)")
    void deveRejeitarObrigatorioEsvaziadoNuloEMegastringEConverterOpcionalEmNull() {
        for (String esvaziado : new String[] {null, "", "   ", "===++--@@||", "---", "''"}) {
            InvalidCuidadorPayloadException ex =
                    assertThrows(
                            InvalidCuidadorPayloadException.class,
                            () -> CsvSanitizer.higienizarTextoObrigatorio(esvaziado, "nome", 120));
            assertEquals("INVALID_PAYLOAD", ex.getCode());
        }

        for (String placeholder :
                new String[] {
                    null, "", "   ", "-", "--", "---", "—", "–", "N/A", "na", "S/N", "nenhum", "==="
                }) {
            assertNull(CsvSanitizer.higienizarTextoOpcional(placeholder, "quarto", 120));
        }

        String comByteNulo = "Ana\u0000Souza";
        assertThrows(
                InvalidCuidadorPayloadException.class,
                () -> CsvSanitizer.higienizarTextoObrigatorio(comByteNulo, "nome", 120));
        assertThrows(
                InvalidCuidadorPayloadException.class,
                () -> CsvSanitizer.higienizarTextoOpcional(comByteNulo, "quarto", 120));
        assertThrows(
                InvalidCuidadorPayloadException.class,
                () -> CsvSanitizer.higienizarTelefoneOpcional(comByteNulo, "telefone", 32));

        String acimaDoTetoBruto = " ".repeat(CsvSanitizer.MAX_RAW_INPUT_LENGTH) + "Ana";
        assertThrows(
                InvalidCuidadorPayloadException.class,
                () -> CsvSanitizer.higienizarTextoObrigatorio(acimaDoTetoBruto, "nome", 120));
    }

    @Test
    @DisplayName(
            "CsvSanitizer preserva telefones E.164 (+55), DDD entre parenteses e ramais, rejeitando formulas, notacao cientifica e parenteses invalidos (S06, S07)")
    void deveValidarGramaticaSimetricaDeTelefonePreservandoE164EDDDBrasileiro() {
        assertEquals(
                "+55 11 90000-0010",
                CsvSanitizer.higienizarTelefoneOpcional("+55 11 90000-0010", "telefone", 32));
        assertEquals(
                "+55 11 90000-0010",
                CsvSanitizer.higienizarTelefoneOpcional("'+55 11 90000-0010", "telefone", 32));
        assertEquals(
                "+55 (11) 90000-0010",
                CsvSanitizer.higienizarTelefoneOpcional("  '+55 (11) 90000-0010 ", "telefone", 32));
        assertEquals(
                "(11) 90000-0010",
                CsvSanitizer.higienizarTelefoneOpcional("(11) 90000-0010", "telefone", 32));
        assertEquals(
                "(011) 90000-0010",
                CsvSanitizer.higienizarTelefoneOpcional("(011) 90000-0010", "telefone", 32));
        assertEquals(
                "011 90000-0010",
                CsvSanitizer.higienizarTelefoneOpcional("'011 90000-0010", "telefone", 32));
        assertEquals(
                "(11) 3000-0106 ramal 12",
                CsvSanitizer.higienizarTelefoneOpcional("(11) 3000-0106 ramal 12", "telefone", 32));
        assertEquals(
                "(11) 3000-0106 ext. 4",
                CsvSanitizer.higienizarTelefoneOpcional("(11) 3000-0106 ext. 4", "telefone", 32));
        assertEquals(
                "(11) 3000-0106 #200",
                CsvSanitizer.higienizarTelefoneOpcional("(11) 3000-0106 #200", "telefone", 32));
        assertEquals(
                "+55 (11) 90000-0010 ramal 1234",
                CsvSanitizer.higienizarTelefoneOpcional(
                        "+55 (11) 90000-0010 ramal 1234", "telefone", 32));

        for (String placeholder :
                new String[] {
                    null, "", "   ", "-", "--", "---", "—", "–", "n/a", "s/n", "nenhum"
                }) {
            assertNull(CsvSanitizer.higienizarTelefoneOpcional(placeholder, "telefone", 32));
        }

        for (String invalido :
                new String[] {
                    "+",
                    "===",
                    "+CMD()",
                    "+=1+1",
                    "=HYPERLINK(\"http://evil.example\")",
                    "@SUM(1,2)",
                    "5,51199E+12",
                    "5.51E+12",
                    "(11 90000-0010",
                    "11) 90000-0010",
                    "((11)) 90000-0010",
                    "() 90000-0010",
                    "12 ramal 345678",
                    "1-2-3-4-5 ramal 678901",
                    "+55 11 90000-0010-99999",
                    LgpdPrivacyMasker.MASCARA_TELEFONE_LGPD
                }) {
            InvalidCuidadorPayloadException ex =
                    assertThrows(
                            InvalidCuidadorPayloadException.class,
                            () -> CsvSanitizer.higienizarTelefoneOpcional(invalido, "telefone", 32),
                            "Deveria rejeitar telefone invalido: " + invalido);
            assertEquals("INVALID_PAYLOAD", ex.getCode());
        }
    }

    @Test
    @DisplayName(
            "LgpdPrivacyMasker pseudonimiza nomes PT-BR com particulas, preserva acentos NFD->NFC e mascara telefones sem expor digitos (S10, S11, S12, S13)")
    void deveMascararNomesPtBrComParticulasNfdETelefonesPreservandoImutabilidade() {
        assertEquals("Carlos R.", LgpdPrivacyMasker.mascararNomePessoa("Carlos Rocha"));
        assertEquals("Ana", LgpdPrivacyMasker.mascararNomePessoa("  Ana  "));
        assertEquals("João C.", LgpdPrivacyMasker.mascararNomePessoa("João da Conceição"));
        assertEquals(
                "Maria L.", LgpdPrivacyMasker.mascararNomePessoa("Maria de Lurdes dos Santos"));
        assertEquals("José S.", LgpdPrivacyMasker.mascararNomePessoa("José e Silva"));
        assertEquals("Helena Á.", LgpdPrivacyMasker.mascararNomePessoa("Helena das Águas"));
        assertEquals("Pedro M.", LgpdPrivacyMasker.mascararNomePessoa("Pedro do Monte"));
        assertEquals("João D.", LgpdPrivacyMasker.mascararNomePessoa("João da"));
        assertEquals("Maria D.", LgpdPrivacyMasker.mascararNomePessoa("Maria de dos"));

        // Entrada Unicode NFD decomposta ('A' + combining acute U+0301) -> inicial deve manter
        // acento "Á."
        assertEquals(
                "João Á.",
                LgpdPrivacyMasker.mascararNomePessoa("Jo\u0061\u0303o \u0041\u0301vila"));
        assertEquals(
                "João Á.",
                LgpdPrivacyMasker.mascararNomePessoa("Jo\u0061\u0303o \u0061\u0301vila"));
        assertEquals("", LgpdPrivacyMasker.mascararNomePessoa(null));
        assertEquals("", LgpdPrivacyMasker.mascararNomePessoa("   "));

        // C1: Fechamento algebrico MAX_TEXT_LENGTH = 120 (surrogate-safe) quando sobrenome tem 1
        // caractere
        String nomeFronteira120 = "A".repeat(118) + " B";
        assertEquals(120, nomeFronteira120.length());
        String mascaradoFronteira120 = LgpdPrivacyMasker.mascararNomePessoa(nomeFronteira120);
        assertEquals(120, mascaradoFronteira120.length());
        assertEquals("A".repeat(117) + " B.", mascaradoFronteira120);

        // Par substituto UTF-16 na posicao de corte (indice 116..117) jamais e partido ao meio
        String nomeSurrogateFronteira = "A".repeat(116) + "\uD835\uDC00 B";
        assertEquals(120, nomeSurrogateFronteira.length());
        String mascaradoSurrogate = LgpdPrivacyMasker.mascararNomePessoa(nomeSurrogateFronteira);
        assertTrue(mascaradoSurrogate.length() <= CuidadorService.MAX_TEXT_LENGTH);
        assertEquals("A".repeat(116) + " B.", mascaradoSurrogate);
        assertFalse(Character.isHighSurrogate(mascaradoSurrogate.charAt(115)));

        assertNull(LgpdPrivacyMasker.mascararTelefone(null));
        assertNull(LgpdPrivacyMasker.mascararTelefone("   "));
        String telMascarado = LgpdPrivacyMasker.mascararTelefone("+55 11 90000-0010");
        assertEquals("(••) •••••-••••", telMascarado);
        assertTrue(telMascarado.chars().noneMatch(c -> c >= '0' && c <= '9'));

        Cuidador original =
                new Cuidador(
                        3L,
                        "Carlos Rocha",
                        "Cobertura",
                        "Noturno (19h-07h)",
                        "Quarto 03",
                        "+55 11 90000-0010",
                        8,
                        "João da Conceição");

        List<Cuidador> projetada = LgpdPrivacyMasker.projetarListaPublica(List.of(original));
        assertEquals(1, projetada.size());
        assertThrows(UnsupportedOperationException.class, projetada::clear);

        Cuidador publico = projetada.get(0);
        assertNotSame(original, publico);
        assertEquals(3L, publico.getId());
        assertEquals("Carlos R.", publico.getNome());
        assertEquals("Cobertura", publico.getStatus());
        assertEquals("Noturno (19h-07h)", publico.getTurno());
        assertEquals("Quarto 03", publico.getQuarto());
        assertEquals("(••) •••••-••••", publico.getTelefone());
        assertEquals(8, publico.getHorasDescanso());
        assertEquals("João C.", publico.getSubstituto());

        // Garante que a instancia original permaneceu 100% intacta
        assertEquals("Carlos Rocha", original.getNome());
        assertEquals("+55 11 90000-0010", original.getTelefone());
        assertEquals("João da Conceição", original.getSubstituto());
    }

    @Test
    @DisplayName(
            "LgpdPrivacyMasker.validarParametroPrivacy bloqueia HTTP Parameter Pollution e coercao silenciosa (S15)")
    void deveValidarParametroPrivacyContraHppECoercaoFrouxa() {
        assertFalse(LgpdPrivacyMasker.validarParametroPrivacy(null));
        assertFalse(LgpdPrivacyMasker.validarParametroPrivacy(List.of()));
        assertFalse(LgpdPrivacyMasker.validarParametroPrivacy(Arrays.asList((String) null)));
        assertFalse(LgpdPrivacyMasker.validarParametroPrivacy(List.of("false")));
        assertFalse(LgpdPrivacyMasker.validarParametroPrivacy(List.of(" FALSE ")));
        assertTrue(LgpdPrivacyMasker.validarParametroPrivacy(List.of("true")));
        assertTrue(LgpdPrivacyMasker.validarParametroPrivacy(List.of(" TrUe ")));

        for (List<String> invalido :
                List.of(
                        List.of(""),
                        List.of("   "),
                        List.of("1"),
                        List.of("0"),
                        List.of("yes"),
                        List.of("sim"),
                        List.of("banana"),
                        List.of("true\u0000"),
                        List.of("false", "true"),
                        List.of("true", "true"))) {
            InvalidCuidadorPayloadException ex =
                    assertThrows(
                            InvalidCuidadorPayloadException.class,
                            () -> LgpdPrivacyMasker.validarParametroPrivacy(invalido));
            assertEquals("INVALID_PAYLOAD", ex.getCode());
        }
    }
}
