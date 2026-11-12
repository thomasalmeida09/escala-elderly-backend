# 🏛️ Architecture Decision Records (ADRs) — Backend (`escala-elderly-backend`)

> **Projeto:** Escala Elderly — API REST & Motor de Regras de Escala  
> **Repositório:** [`escala-elderly-backend`](https://github.com/thomasalmeida09/escala-elderly-backend) (Backend Java / Spring Boot)  
> **Última Atualização:** 11 de Novembro de 2026  

Este documento registra as **Decisões Arquiteturais (ADRs)** do **Backend**, divididas em **2 Grupos**:
1. **Grupo 1 — Decisões Já Tomadas no Projeto (V1.0):** O que já foi implementado no repositório inicial, **o porquê (motivação)** e os **trade-offs (vantagens vs. custos/dívidas técnicas)**.
2. **Grupo 2 — Decisões de Governança, Processos e Evolução (Kickoff do Time):** Como gerenciamos tarefas, padrão de formatação de código Java, Continuous Integration (CI), Pull Requests (PRs) e escopo das Sprints.

### 🖼️ Visualização Interativa das ADRs no PR Lens
- 🏗️ **[Canvas Arquitetura Completa & ADRs (4 Diagramas + Walkthrough)](https://prlens.dev/c/L-2rShWu_j0WpKt-jT0Cog)**
- 👶 **[Canvas ELI5 — Como o Escala Elderly Resolve uma Falta (Onboarding Visual)](https://prlens.dev/c/x2StISYcAmXvy49MhetrsQ)**

[![Arquitetura Escala Elderly — PR Lens](https://prlens.dev/c/L-2rShWu_j0WpKt-jT0Cog.svg)](https://prlens.dev/c/L-2rShWu_j0WpKt-jT0Cog)

---

## 📗 GRUPO 1: Decisões Já Tomadas no Backend (`STATUS: ACEITA / IMPLEMENTADA NA V1.0`)

---

### 📌 ADR-BE-001: Arquitetura REST API Desacoplada com CORS Habilitado e Erros Uniformes
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`CuidadorController.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/CuidadorController.java), [`WebApiConfig.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/WebApiConfig.java), [`application.properties`](../../escala-elderly-api/src/main/resources/application.properties) e [`ApiExceptionHandler.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/ApiExceptionHandler.java).
- **Decisão:** Expor o Backend como uma API RESTful independente na porta `8080` (`@RestController`) com política CORS centralizada no `CorsFilter` de `WebApiConfig` para todo o prefixo `/api/**` (parametrizada via `escala.cors.allowed-origins` em `application.properties`, substituindo o `@CrossOrigin` fixo inicial do controller e garantindo cabeçalhos CORS inclusive em respostas `404`/`405`/`415`) e envelope padronizado de erro `ApiErrorResponse` (`code`, `message`, `path`, `timestamp`) para consumo direto pela SPA React (`localhost:5173`).
- **Por quê (Motivação):**
  - Separar responsabilidades entre a camada de apresentação (React) e a camada de regras/dados (Java), permitindo evolução independente, preflight `OPTIONS` determinístico em mutações `PATCH`/`POST` e diagnóstico claro de erros via navegador, Postman ou cURL.
- **Trade-offs (Vantagens vs. Custos Assumidos):**
  - ✅ **Vantagens:** Integração imediata sem bloqueio de CORS em ambiente de desenvolvimento e restrição explícita `403 Forbidden` para origens não autorizadas em produção; arquitetura limpa cliente-servidor sem vazamento de *stack traces*.
  - ✅ **Dívida Técnica Quitada na Issue #4 (`ADR-BE-011`):** As origens permitidas de CORS foram externalizadas em `escala.cors.allowed-origins` (`application.properties`), com normalização automática de barras finais `/` e validação *fail-fast* de esquema `http(s)://`.

---

### 📌 ADR-BE-002: Stack Tecnológica — Java 17 LTS + Spring Boot 4.1.0 + Gradle Wrapper
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`build.gradle`](../../escala-elderly-api/build.gradle) e [`EscalaElderlyApiApplication.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/EscalaElderlyApiApplication.java).
- **Decisão:** Construir a API sobre **Java 17 LTS** e **Spring Boot 4.1.0** (`spring-boot-starter-webmvc`), utilizando **Gradle Wrapper (`./gradlew`)** com *Java Toolchain* configurada para a versão 17.
- **Por quê (Motivação):**
  - O Spring Boot fornece servidor Tomcat embutido, injeção de dependência nativa e serialização JSON automática; o Gradle Wrapper garante que todos os desenvolvedores usem exatamente a mesma versão do Gradle e do Java 17 sem configuração manual complexa.
- **Trade-offs (Vantagens vs. Custos Assumidos):**
  - ✅ **Vantagens:** Ecossistema robusto de mercado, tipagem estática forte e suporte nativo a JUnit (`useJUnitPlatform()`).
  - ⚠️ **Custos / Cuidados:** O projeto Gradle encontra-se dentro do subdiretório [`escala-elderly-api/`](../../escala-elderly-api/), exigindo que comandos `./gradlew` e pipelines de CI apontem para `working-directory: ./escala-elderly-api`.

---

### 📌 ADR-BE-003: Modelagem de Domínio Imutável, DTOs em Java `record` e Serialização Estrita via Jackson 3
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`Cuidador.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/Cuidador.java), [`StatusUpdateRequest.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/StatusUpdateRequest.java), [`CuidadorBatchItem.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/CuidadorBatchItem.java), [`ApiErrorResponse.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/ApiErrorResponse.java) e [`WebApiConfig.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/WebApiConfig.java).
- **Decisão:** Representar a entidade `Cuidador` através de uma classe Java **100% imutável** (`private final` em todos os campos, zero métodos `set*`, `@JsonCreator` canônico e `@JsonInclude(JsonInclude.Include.NON_NULL)`, preservando também o construtor enxuto de 3 argumentos), combinada com Java 17 `record`s para os DTOs de entrada/saída (`StatusUpdateRequest`, `CuidadorBatchItem`, `ApiErrorResponse`) e `JsonMapperBuilderCustomizer` bloqueando coerções silenciosas escalares no Jackson 3 (`3.7 -> 3`, `true -> "true"`).
- **Por quê (Motivação):**
  - Evitar bibliotecas mágicas externas (como Lombok), blindar matematicamente os snapshots em memória do `CuidadorService` contra mutações externas por referência (*Shy Code*), omitir chaves nulas no JSON para paridade exata com propriedades opcionais `?` do TypeScript e rejeitar payloads malformados com `400 Bad Request`.
- **Trade-offs (Vantagens vs. Custos Assumidos):**
  - ✅ **Vantagens:** Zero configuração de *Annotation Processors* nas IDEs, *thread-safety* estrutural por imutabilidade e contrato JSON previsível entre Java e TypeScript.
  - ⚠️ **Custos / Cuidados:** Toda atualização de estado instancia um novo `Cuidador` imutável dentro de `CuidadorService` em vez de mutar campos *in-place*, custo desprezível (nanossegundos) que elimina inteiramente condições de corrida.

---

### 📌 ADR-BE-004: Código Comentado Didaticamente para Nivelamento da Equipe
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** Comentários explicativos em [`Cuidador.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/Cuidador.java), [`CuidadorController.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/CuidadorController.java) e [`CuidadorService.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/CuidadorService.java).
- **Decisão:** Documentar no próprio código o papel de cada anotação do Spring (`@RestController`, `@CrossOrigin`, `@GetMapping`, `@PatchMapping`, `@PostMapping`, `@Service`) e das decisões de imutabilidade e concorrência.
- **Por quê (Motivação):**
  - Garantir que todos os integrantes do projeto compreendam o funcionamento interno da API, acelerando a curva de aprendizado.
- **Trade-offs (Vantagens vs. Custos Assumidos):**
  - ✅ **Vantagens:** Excelente didática e inclusão técnica de todo o time.
  - ⚠️ **Dívida Técnica Mapeada (Resolvida na Issue #1):** No commit inicial (`fee6bf1`), o arquivo `CuidadorController.java` acabou truncado na linha 30, evidenciando a necessidade imediata da **Issue #1** e do **Continuous Integration (`ADR-BE-008`)**.

---

## 📙 GRUPO 2: Decisões de Governança e Evolução do Backend (`STATUS: ATIVAS NAS SPRINTS`)

---

### 🗳️ ADR-BE-005: Escopo do Backend Dividido em 3 Sprints Evolutivas
- **Status:** `ACEITA / IMPLEMENTADA`
- **Pergunta:** *"Qual o escopo do projeto no Backend?"*
- **Decisão:** Evoluir a API em **3 etapas claras**:
  1. **🟢 Sprint 1 (`sprint-1: fundacao` — Issues [#1](https://github.com/thomasalmeida09/escala-elderly-backend/issues/1), [#2](https://github.com/thomasalmeida09/escala-elderly-backend/issues/2) e [#5](https://github.com/thomasalmeida09/escala-elderly-backend/issues/5)):**
     - Corrigir a compilação de `CuidadorController.java`, adicionar `.gitignore`/Spotless/CI (#1 e #5);
     - Alinhar a semente inicial de dados com o Frontend (`id: 3` = `Carlos Rocha`, status `"Presente"`) e criar `CuidadorService` em memória thread-safe com os endpoints `PATCH /api/cuidadores/{id}/status`, `POST /api/cuidadores/batch` e `POST /api/cuidadores/reset` (#2).
  2. **🔵 Sprint 2 (`sprint-2: mvp-core`):**
     - Suporte completo à persistência dos lotes importados via planilha CSV (`POST /api/cuidadores/batch`) e registro de cobertura de falta (`substituto`, `telefone`, `horasDescanso`).
  3. **🟣 Sprint 3 (`sprint-3: evolucao` — Issues [#3](https://github.com/thomasalmeida09/escala-elderly-backend/issues/3) e [#4](https://github.com/thomasalmeida09/escala-elderly-backend/issues/4)):**
     - Endpoint de tempo real `GET /api/cuidadores/stream` via **Server-Sent Events (`SseEmitter`)** (#3) e proteção LGPD / sanitização contra *CSV Formula Injection* (#4).

---

### 🗳️ ADR-BE-006: Gestão de Tarefas via GitHub Issues + GitHub Projects (Kanban Unificado)
- **Status:** `ACEITA / IMPLEMENTADA`
- **Pergunta:** *"Como vamos gerenciar as tarefas?"*
- **Decisão:** Gerenciar todas as demandas do Backend junto com o Frontend no quadro unificado do **GitHub Projects** (`📋 Backlog` $\rightarrow$ `🎯 Sprint 1 (To Do)` $\rightarrow$ `🔨 In Progress` $\rightarrow$ `👀 In Review (PR)` $\rightarrow$ `✅ Done`), vinculando cada PR à sua respectiva Issue.

---

### 🗳️ ADR-BE-007: Padrão de Formatação de Código e Convenções Java
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`escala-elderly-api/build.gradle`](../../escala-elderly-api/build.gradle), [`.editorconfig`](../../.editorconfig), [`.gitattributes`](../../.gitattributes) e [`.gitignore`](../../.gitignore).
- **Pergunta:** *"Qual o padrão de formatação do código no Backend?"*
- **Decisão:**
  1. **Formatador Automático:** Adotar o plugin **Spotless (`com.diffplug.spotless:8.10.4`)** com **Google Java Format `1.22.0` (modo `.aosp()` / 4 espaços de indentação, `LineEnding.UNIX`, `UTF-8`)** no `build.gradle`:
     - Para formatar antes de commitar: `./gradlew spotlessApply` (ou `.\gradlew.bat spotlessApply` no Windows)
     - Para verificar localmente e na pipeline de CI: `./gradlew spotlessCheck` (acoplado automaticamente ao `./gradlew check`)
  2. **EditorConfig & GitAttributes (`.editorconfig` / `.gitattributes`):** `charset = utf-8` (sem BOM), `end_of_line = lf`, `indent_size = 4` para Java/Gradle e `2` para YAML/JSON/Markdown.
  3. **Convenções Java:**
     - Classes em `PascalCase` (`CuidadorController`, `CuidadorService`), métodos e variáveis em `camelCase` (`listarCuidadores`, `atualizarStatus`);
     - Separar responsabilidades em classes coesas mantendo os comentários didáticos essenciais (`ADR-BE-004`).

---

### 🗳️ ADR-BE-008: Continuous Integration (CI) & Portaria de Segurança/LGPD no Backend com GitHub Actions
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml), [`.github/ruleset-main-protection.json`](../../.github/ruleset-main-protection.json) e [`GovernanceHygieneTest.java`](../../escala-elderly-api/src/test/java/escala_elderly_api/GovernanceHygieneTest.java).
- **Pergunta:** *"Como vai funcionar o Continuous Integration?"*
- **Decisão:** Configurar `.github/workflows/ci.yml` (`ubuntu-24.04`, `permissions: contents: read`, Actions fixadas por SHA de 40 caracteres em runtime Node 24) executando 2 jobs obrigatórios em todo `push` e `pull_request` para a `main`:
  1. **`Java 17 Spring Boot Compile, Audit & JUnit 5 Gate` (`backend-ci`):**
     - Setup do **JDK 17 (Eclipse Temurin)** com cache do Gradle (`gradle/actions/setup-gradle@v6.4.1`) em `./escala-elderly-api`;
     - Auditoria estática *Broken-Windows* bloqueando blocos `catch {}` vazios;
     - Verificação explícita de formatação `./gradlew spotlessCheck --no-daemon` seguida de compilação e testes JUnit `./gradlew check test -x spotlessCheck --no-daemon --warning-mode=all` (incluindo *Fitness Functions* de governança em `GovernanceHygieneTest`).
  2. **`Secret, Credential & LGPD Clean-Room Hygiene Gate` (`clean-room-security`):**
     - Bloqueio determinístico de BOM UTF-8 (`0xEF 0xBB 0xBF`), arquivos `.env`, senhas hardcoded em `application.properties`, chaves privadas/tokens, PDFs de referência e vazamentos de CPF/CNPJ ou e-mails reais fora da RFC 2606 (`@example.com`).
- **Motivação & Trade-off:** Garante que nenhum commit com erro de compilação, formatação divergente, deriva documental em `README.md`, teste quebrado ou vazamento de segredos/PII LGPD possa ser mergeado na `main`.

---

### 🗳️ ADR-BE-009: Governança de Branches, Commits e Pull Requests (PRs)
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`.github/pull_request_template.md`](../../.github/pull_request_template.md), [`.github/CODEOWNERS`](../../.github/CODEOWNERS) e [`.github/ruleset-main-protection.json`](../../.github/ruleset-main-protection.json).
- **Pergunta:** *"Como vão ser os PRs?"*
- **Decisão:**
  1. **Branch `main` Protegida (Ruleset):** Toda alteração é desenvolvida em branch dedicada (`fix/issue-01-...`, `feat/issue-02-...`), bloqueando *force-pushes* e exigindo histórico linear.
  2. **Conventional Commits & Fowler's Two Hats:** `feat(api): ...`, `feat(domain): ...`, `fix(api): ...`, `docs(governance): ...`, `test(api): ...`, `ci(backend): ...`, `style(api): ...`.
  3. **Critérios para Merge:** Template de PR preenchido (`Closes #Número`) + **2 Checks da CI Verdes (`backend-ci` e `clean-room-security`)** + **1 Aprovação (Code Review roteado via `CODEOWNERS`)** + **Squash and Merge**.

---

### 🗳️ ADR-BE-010: Contrato JSON Canônico Alinhado ao Frontend e Motor MVCC em Memória (`CuidadorService`)
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`Cuidador.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/Cuidador.java), [`StatusCuidador.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/StatusCuidador.java), [`CuidadorService.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/CuidadorService.java) e [`CuidadorController.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/CuidadorController.java).
- **Decisão:**
  - Unificar os IDs (`1..4`, com `id: 3 = Carlos Rocha`) e os 4 status canônicos (`Presente`, `Faltou`, `Folga`, `Cobertura`) da API com o Frontend para eliminar definitivamente o bug em que clicar em *"Falta de Carlos"* alterava o registro errado:
    ```json
    [
      { "id": 1, "nome": "Ana Souza", "status": "Presente", "turno": "Diurno (07h-19h)", "quarto": "Quarto 01" },
      { "id": 2, "nome": "Lucas Lima", "status": "Presente", "turno": "Diurno (07h-19h)", "quarto": "Quarto 02" },
      { "id": 3, "nome": "Carlos Rocha", "status": "Presente", "turno": "Noturno (19h-07h)", "quarto": "Quarto 03" },
      { "id": 4, "nome": "Mariana Costa", "status": "Presente", "turno": "Noturno (19h-07h)", "quarto": "Quarto 04" }
    ]
    ```
  - Manter a persistência ativa em um `CuidadorService` *thread-safe* baseado em snapshot imutável MVCC (`AtomicReference<List<Cuidador>>`), expondo leituras *lock-free* em $O(1)$ (`GET /api/cuidadores`) e mutações atômicas *All-or-Nothing* (`PATCH /api/cuidadores/{id}/status`, `POST /api/cuidadores/batch` com Auto-ID monotônico `maxExplicitId + 1` e `POST /api/cuidadores/reset`), rodando instantaneamente com `./gradlew bootRun` sem exigir banco de dados externo.

---

### 🗳️ ADR-BE-011: Proteção de Dados LGPD Read-Time (`?privacy=true`), Sanitização OWASP CSV Injection e CORS Parametrizável
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`LgpdPrivacyMasker.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/LgpdPrivacyMasker.java), [`CsvSanitizer.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/CsvSanitizer.java), [`CuidadorService.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/CuidadorService.java), [`CuidadorController.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/CuidadorController.java), [`WebApiConfig.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/WebApiConfig.java) e [`application.properties`](../../escala-elderly-api/src/main/resources/application.properties).
- **Decisão:**
  1. **Projeção LGPD *Read-Time* (`GET /api/cuidadores?privacy=true`):** Implementar em `LgpdPrivacyMasker` a minimização e pseudonimização em tempo de leitura (Lei nº 13.709/2018 — Art. 6º III/VII e Art. 13) sem jamais mutar o snapshot canônico MVCC (`snapshotRef`). Nomes civis e `substituto` são abreviados em paridade 1:1 com o Frontend (`"Carlos Rocha"` $\rightarrow$ `"Carlos R."`, `"João da Conceição"` $\rightarrow$ `"João C."`, normalização Unicode `NFC` e suporte a partículas PT-BR `da/de/do/das/dos/e`), `telefone` não-nulo é mascarado como `"(••) •••••-••••"` (zero dígitos expostos) e `horasDescanso` é preservado para não desarmar a trava de fadiga interjornada (`< 11h`, Art. 66 da CLT). O validador estrito bloqueia coerções frouxas (`?privacy=1`, `?privacy=`) e *HTTP Parameter Pollution* (`?privacy=false&privacy=true`) com `400 INVALID_PAYLOAD`.
  2. **Sanitização Iterativa em Ponto Fixo contra OWASP CSV Formula Injection (CWE-1236):** Isolar em `CsvSanitizer` a purga de invisíveis Unicode/BOM (`\uFEFF`, `\u200B..\u200F`, bidi), normalização de separadores de linha (`\r`, `\n`, `\t`, `\u0085`, `\u2028`, `\u2029`) para espaço único e remoção iterativa de gatilhos iniciais (`=`, `+`, `-`, `@`, `|`, `'`, homóglifos fullwidth) em `POST /api/cuidadores/batch` e `PATCH /api/cuidadores/{id}/status`, mantendo `status` estritamente não-sanitizado (`"=Presente"` $\rightarrow$ `400 INVALID_STATUS`) e preservando gramática simétrica de `telefone` (`+55` ITU-T E.164, DDD entre parênteses `(11) 90000-0010` e sufixos `ramal`/`ext`/`#`).
  3. **CORS Centralizado e Parametrizável (`escala.cors.allowed-origins`):** Remover `@CrossOrigin` fixo do controller e parametrizar o `CorsFilter` de `WebApiConfig` via `application.properties` (default `*` em desenvolvimento; lista explícita com remoção de barra final `/` e rejeição `403 Forbidden` para origens não autorizadas em produção, quitando a dívida registrada em `ADR-BE-001`).
- **Motivação & Trade-offs:**
  - ✅ **Vantagens:** Segurança em profundidade e conformidade LGPD em murais de enfermagem com `0` dependências novas no `build.gradle`, mantendo leituras normais `privacy=false` em $O(1)$ *lock-free* e 100% de compatibilidade com `parseCuidadorList` e `csvScheduleCodec.ts` do Frontend.
  - ⚠️ **Custos / Cuidados:** Sob `?privacy=true`, uma lista imutável projetada é alocada por requisição ($O(N)$ para $N \le 500$ cuidadores, microssegundos em memória), preservando intacto o estado canônico compartilhado.

