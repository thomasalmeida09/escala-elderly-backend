package escala_elderly_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fitness Functions arquiteturais que blindam paridade 1:1 da arvore de README.md, integridade
 * referencial das ADR-BE-001..011, invariantes da esteira CI e higiene Clean-Room/LGPD.
 */
class GovernanceHygieneTest {

    private static final Pattern TREE_FENCE_PATTERN =
            Pattern.compile("```text\\n(escala-elderly-backend/[\\s\\S]*?)```");
    private static final Pattern TREE_LINE_PATTERN = Pattern.compile("^([│ ]*)(?:├── |└── )(.+)$");
    private static final Pattern ANY_ADR_REF_PATTERN =
            Pattern.compile("\\bADR-(?:[A-Z]+-)?\\d+\\b");
    private static final Pattern CANONICAL_BE_ADR_PATTERN = Pattern.compile("^ADR-BE-\\d{3}$");
    private static final Pattern ADR_HEADING_PATTERN =
            Pattern.compile("^### .*?(ADR-BE-\\d{3}):", Pattern.MULTILINE);
    private static final Pattern EMPTY_CATCH_PATTERN =
            Pattern.compile("catch\\s*\\([^)]+\\)\\s*\\{\\s*\\}");
    private static final Pattern CPF_CNPJ_PATTERN =
            Pattern.compile(
                    "\\b[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}\\b|\\b[0-9]{2}\\.[0-9]{3}\\.[0-9]{3}/[0-9]{4}-[0-9]{2}\\b");
    private static final Pattern NON_RESERVED_EMAIL_PATTERN =
            Pattern.compile(
                    "\\b[A-Za-z0-9._%+-]+@(?!([A-Za-z0-9-]+\\.)?(example\\.(com|org|net)|test|invalid|localhost)\\b)[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        return Files.exists(cwd.resolve("escala-elderly-api")) ? cwd : cwd.getParent();
    }

    private static String readUtf8(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName(
            "1. Garante paridade 1:1 entre a arvore de README.md e todos os arquivos .java do projeto")
    void deveGarantirParidade1Para1DaArvoreDoReadmeComFilesystem() throws IOException {
        Path repoRoot = resolveRepoRoot();
        String readme = readUtf8(repoRoot.resolve("README.md"));

        Matcher fenceMatcher = TREE_FENCE_PATTERN.matcher(readme);
        assertTrue(
                fenceMatcher.find(),
                "Bloco ```text escala-elderly-backend/ nao encontrado em README.md");

        String[] lines = fenceMatcher.group(1).trim().split("\n");
        List<String> stack = new ArrayList<>();
        Set<String> documentedPaths = new TreeSet<>();

        for (int i = 1; i < lines.length; i++) {
            String rawLine = lines[i];
            String cleanLine = rawLine.split("#")[0].stripTrailing();
            Matcher branchMatcher = TREE_LINE_PATTERN.matcher(cleanLine);
            assertTrue(
                    branchMatcher.matches(), "Linha malformada na arvore de README.md: " + rawLine);

            String prefix = branchMatcher.group(1);
            String entrySegment = branchMatcher.group(2).trim();
            assertEquals(
                    0,
                    prefix.length() % 4,
                    "Indentacao invalida na arvore de README.md: " + rawLine);
            int depth = prefix.length() / 4;

            while (stack.size() > depth) {
                stack.remove(stack.size() - 1);
            }

            if (entrySegment.endsWith("/")) {
                String cleanDir = entrySegment.substring(0, entrySegment.length() - 1);
                stack.add(cleanDir);
                String relDir = String.join("/", stack) + "/";
                documentedPaths.add(relDir);

                Path dirOnDisk = repoRoot.resolve(String.join("/", stack));
                assertTrue(
                        Files.isDirectory(dirOnDisk),
                        "Diretorio documentado em README.md nao existe: " + relDir);
            } else {
                String parentPrefix = stack.isEmpty() ? "" : String.join("/", stack) + "/";
                for (String leaf : entrySegment.split("\\s+/\\s+")) {
                    String relFile = parentPrefix + leaf.trim();
                    documentedPaths.add(relFile);

                    Path fileOnDisk = repoRoot.resolve(relFile);
                    assertTrue(
                            Files.isRegularFile(fileOnDisk),
                            "Arquivo documentado em README.md nao existe no disco: " + relFile);
                }
            }
        }

        Set<String> documentedJavaFiles = new TreeSet<>();
        for (String docPath : documentedPaths) {
            if (docPath.endsWith(".java")) {
                documentedJavaFiles.add(docPath);
            }
        }

        Set<String> actualJavaFiles = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(repoRoot.resolve("escala-elderly-api/src"))) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(
                            p ->
                                    actualJavaFiles.add(
                                            repoRoot.relativize(p).toString().replace('\\', '/')));
        }

        assertEquals(
                actualJavaFiles,
                documentedJavaFiles,
                "Deriva detectada entre arquivos .java reais e a arvore arquitetural de README.md!");
    }

    @Test
    @DisplayName(
            "2. Garante integridade referencial das ADR-BE-001..011 em docs/adr, README e PR Template")
    void deveGarantirIntegridadeReferencialDasAdrs() throws IOException {
        Path repoRoot = resolveRepoRoot();
        String adrCatalog = readUtf8(repoRoot.resolve("docs/adr/README.md"));
        String prTemplate = readUtf8(repoRoot.resolve(".github/pull_request_template.md"));
        String readme = readUtf8(repoRoot.resolve("README.md"));

        List<String> headings = new ArrayList<>();
        Matcher headingMatcher = ADR_HEADING_PATTERN.matcher(adrCatalog);
        while (headingMatcher.find()) {
            headings.add(headingMatcher.group(1));
        }

        List<String> expectedAdrs =
                List.of(
                        "ADR-BE-001",
                        "ADR-BE-002",
                        "ADR-BE-003",
                        "ADR-BE-004",
                        "ADR-BE-005",
                        "ADR-BE-006",
                        "ADR-BE-007",
                        "ADR-BE-008",
                        "ADR-BE-009",
                        "ADR-BE-010",
                        "ADR-BE-011");
        assertEquals(expectedAdrs, headings);

        // Autoteste contra tokens invalidos
        assertFalse(CANONICAL_BE_ADR_PATTERN.matcher(String.join("-", "ADR", "001")).matches());

        Set<String> catalogSet = Set.copyOf(expectedAdrs);
        for (String content : List.of(adrCatalog, prTemplate, readme)) {
            Matcher refMatcher = ANY_ADR_REF_PATTERN.matcher(content);
            boolean foundAny = false;
            while (refMatcher.find()) {
                foundAny = true;
                String token = refMatcher.group();
                assertTrue(
                        CANONICAL_BE_ADR_PATTERN.matcher(token).matches(),
                        "Referencia ADR fora do padrao ADR-BE-XXX: " + token);
                assertTrue(
                        catalogSet.contains(token),
                        "Referencia a ADR inexistente no catalogo: " + token);
            }
            assertTrue(foundAny);
        }

        for (String adrId : expectedAdrs) {
            Pattern implementedPattern =
                    Pattern.compile(
                            "### .*?"
                                    + adrId
                                    + ":[\\s\\S]*?- \\*\\*Status:\\*\\* `ACEITA / IMPLEMENTADA`");
            assertTrue(
                    implementedPattern.matcher(adrCatalog).find(),
                    adrId + " deve estar marcada como ACEITA / IMPLEMENTADA");
        }
    }

    @Test
    @DisplayName(
            "3. Preserva paridade de contextos entre ci.yml e ruleset-main-protection.json com SHAs de 40 chars")
    void devePreservarParidadeCiRulesetEPinagemSha40() throws IOException {
        Path repoRoot = resolveRepoRoot();
        String ciYaml = readUtf8(repoRoot.resolve(".github/workflows/ci.yml"));
        String rulesetJson = readUtf8(repoRoot.resolve(".github/ruleset-main-protection.json"));

        for (String requiredContext :
                List.of(
                        "Java 17 Spring Boot Compile, Audit & JUnit 5 Gate",
                        "Secret, Credential & LGPD Clean-Room Hygiene Gate")) {
            assertTrue(ciYaml.contains("name: " + requiredContext));
            assertTrue(rulesetJson.contains("\"" + requiredContext + "\""));
        }

        assertFalse(ciYaml.contains("ubuntu-latest"));
        Matcher usesMatcher =
                Pattern.compile("^\\s*-?\\s*uses:\\s*([^\\s#]+)", Pattern.MULTILINE)
                        .matcher(ciYaml);
        int usesCount = 0;
        while (usesMatcher.find()) {
            usesCount++;
            String actionRef = usesMatcher.group(1);
            assertTrue(
                    actionRef.matches(
                            "^[a-zA-Z0-9-]+/[a-zA-Z0-9._-]+(?:/[a-zA-Z0-9._-]+)*@[0-9a-f]{40}$"),
                    "Action sem SHA de 40 caracteres: " + actionRef);
        }
        assertTrue(usesCount >= 4);
    }

    @Test
    @DisplayName(
            "4. Audita src/main e src/test contra BOM UTF-8, CRLF, catch vazio, PDFs e PII LGPD")
    void deveGarantirHigieneCleanRoomZeroBomZeroCatchVazioEmSrc() throws IOException {
        // Prova dinamica da letalidade dos regexes sem embarcar PII literal no repositorio
        String probeCpf = String.join(".", "123", "456", "789-00");
        String probeEmail = String.join("@", "gestor", "clinica-real.com.br");
        assertTrue(CPF_CNPJ_PATTERN.matcher(probeCpf).find());
        assertTrue(NON_RESERVED_EMAIL_PATTERN.matcher(probeEmail).find());
        assertFalse(NON_RESERVED_EMAIL_PATTERN.matcher("gestor@example.com").find());

        Path repoRoot = resolveRepoRoot();
        List<String> violacoes = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(repoRoot.resolve("escala-elderly-api/src"))) {
            List<Path> arquivos = walk.filter(Files::isRegularFile).toList();
            assertTrue(arquivos.size() >= 18);

            for (Path arquivo : arquivos) {
                String rel = repoRoot.relativize(arquivo).toString().replace('\\', '/');
                if (rel.toLowerCase().endsWith(".pdf")) {
                    violacoes.add(rel + ": PDF binario proibido em src/");
                    continue;
                }

                byte[] bytes = Files.readAllBytes(arquivo);
                if (bytes.length >= 3
                        && (bytes[0] & 0xFF) == 0xEF
                        && (bytes[1] & 0xFF) == 0xBB
                        && (bytes[2] & 0xFF) == 0xBF) {
                    violacoes.add(rel + ": UTF-8 BOM detectado");
                }
                for (byte b : bytes) {
                    if (b == '\r') {
                        violacoes.add(rel + ": quebra de linha CRLF detectada");
                        break;
                    }
                }

                String texto = new String(bytes, StandardCharsets.UTF_8);
                if (rel.endsWith(".java") && EMPTY_CATCH_PATTERN.matcher(texto).find()) {
                    violacoes.add(rel + ": bloco catch vazio proibido (Broken Windows)");
                }
                if (CPF_CNPJ_PATTERN.matcher(texto).find()) {
                    violacoes.add(rel + ": padrao de CPF/CNPJ detectado");
                }
                if (NON_RESERVED_EMAIL_PATTERN.matcher(texto).find()) {
                    violacoes.add(rel + ": e-mail fora de dominio reservado RFC 2606 detectado");
                }
            }
        }

        assertEquals(List.of(), violacoes);
    }
}
