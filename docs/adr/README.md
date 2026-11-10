# 🏛️ Architecture Decision Records (ADRs) — Backend (`escala-elderly-backend`)

> **Projeto:** Escala Elderly — API REST & Motor de Regras de Escala  
> **Repositório:** [`escala-elderly-backend`](https://github.com/thomasalmeida09/escala-elderly-backend) (Backend Java / Spring Boot)  
> **Última Atualização:** 10 de Novembro de 2026  

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

### 📌 ADR-BE-001: Arquitetura REST API Desacoplada com CORS Habilitado
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`CuidadorController.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/CuidadorController.java).
- **Decisão:** Expor o Backend como uma API RESTful independente na porta `8080` (`@RestController`) com `@CrossOrigin(origins = "*")` habilitado para consumo direto pela SPA React (`localhost:5173`).
- **Por quê (Motivação):**
  - Separar responsabilidades entre a camada de apresentação (React) e a camada de regras/dados (Java), permitindo evolução independente e testes diretos via navegador, Postman ou cURL.
- **Trade-offs (Vantagens vs. Custos Assumidos):**
  - ✅ **Vantagens:** Integração imediata sem bloqueio de CORS em ambiente de desenvolvimento; arquitetura limpa cliente-servidor.
  - ⚠️ **Custos / Cuidados:** Em produção/apresentação final, as origens do `@CrossOrigin` devem ser parametrizáveis via `application.properties` (previsto na **Issue #4**).

---

### 📌 ADR-BE-002: Stack Tecnológica — Java 17 LTS + Spring Boot 4.1.0 + Gradle Wrapper
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`build.gradle`](../../escala-elderly-api/build.gradle) e [`EscalaElderlyApiApplication.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/EscalaElderlyApiApplication.java).
- **Decisão:** Construir a API sobre **Java 17 LTS** e **Spring Boot 4.1.0** (`spring-boot-starter-webmvc`), utilizando **Gradle Wrapper (`./gradlew`)** com *Java Toolchain* configurada para a versão 17.
- **Por quê (Motivação):**
  - O Spring Boot fornece servidor Tomcat embutido, injeção de dependência nativa e serialização JSON automática; o Gradle Wrapper garante que todos os desenvolvedores usem exatamente a mesma versão do Gradle e do Java 17 sem configuração manual complexa.
- **Trade-offs (Vantagens vs. Custos Assumidos):**
  - ✅ **Vantagens:** Ecossistema robusto de mercado, tipagem estática forte e suporte nativo a JUnit 5 (`useJUnitPlatform()`).
  - ⚠️ **Custos / Cuidados:** O projeto Gradle encontra-se dentro do subdiretório [`escala-elderly-api/`](../../escala-elderly-api/), exigindo que comandos `./gradlew` e pipelines de CI apontem para `working-directory: ./escala-elderly-api`.

---

### 📌 ADR-BE-003: Modelagem de Domínio Explícita e Serialização via Jackson (`Cuidador.java`)
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`Cuidador.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/Cuidador.java).
- **Decisão:** Representar a entidade `Cuidador` através de uma classe Java explícita com construtor parametrizado e métodos `getters` públicos (`getId()`, `getNome()`, `getStatus()`) para serialização automática em JSON pelo Jackson.
- **Por quê (Motivação):**
  - Evitar "mágica excessiva" de bibliotecas externas (como Lombok) no primeiro commit, garantindo que qualquer membro da equipe entenda exatamente como o objeto Java é construído e transformado em JSON.
- **Trade-offs (Vantagens vs. Custos Assumidos):**
  - ✅ **Vantagens:** Zero problemas de configuração de *Annotation Processors* nas IDEs (VS Code, IntelliJ, Eclipse) da equipe.
  - ⚠️ **Custos / Cuidados:** Ao adicionar novos campos (`turno`, `quarto`) e endpoints `PATCH`/`POST` (que exigem desserialização de JSON de entrada), será necessário incluir construtor padrão vazio `public Cuidador() {}` e `setters` (ou usar Java `record` para DTOs).

---

### 📌 ADR-BE-004: Código Comentado Didaticamente para Nivelamento da Equipe
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** Comentários explicativos em [`Cuidador.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/Cuidador.java) e [`CuidadorController.java`](../../escala-elderly-api/src/main/java/escala_elderly_api/CuidadorController.java).
- **Decisão:** Documentar no próprio código o papel de cada anotação do Spring (`@RestController`, `@CrossOrigin`, `@GetMapping`) e das estruturas do Java (`List`, `ArrayList`).
- **Por quê (Motivação):**
  - Garantir que todos os integrantes do projeto compreendam o funcionamento interno da API, acelerando a curva de aprendizado.
- **Trade-offs (Vantagens vs. Custos Assumidos):**
  - ✅ **Vantagens:** Excelente didática e inclusão técnica de todo o time.
  - ⚠️ **Dívida Técnica Mapeada (Resolvida na Issue #1):** No último commit (`fee6bf1`), o arquivo `CuidadorController.java` acabou truncado na linha 30 (faltando fechar `}}` e `import java.util.ArrayList;`), evidenciando a necessidade imediata da **Issue #1** e do **Continuous Integration (ADR-BE-008)**.

---

## 📙 GRUPO 2: Decisões de Governança e Evolução do Backend (`STATUS: DEFINIDAS PARA INÍCIO DAS SPRINTS`)

---

### 🗳️ ADR-BE-005: Escopo do Backend Dividido em 3 Sprints Evolutivas
- **Pergunta:** *"Qual o escopo do projeto no Backend?"*
- **Decisão:** Evoluir a API em **3 etapas claras**:
  1. **🟢 Sprint 1 (`sprint-1: fundacao` — Issues [#1](https://github.com/thomasalmeida09/escala-elderly-backend/issues/1), [#2](https://github.com/thomasalmeida09/escala-elderly-backend/issues/2) e [#5](https://github.com/thomasalmeida09/escala-elderly-backend/issues/5)):**
     - Corrigir a compilação de `CuidadorController.java`, adicionar `.gitignore`/Spotless/CI (#1 e #5);
     - Alinhar a semente inicial de dados com o Frontend (`id: 3` = `Carlos Rocha`, status `"Presente"`) e criar `CuidadorService` em memória thread-safe com os endpoints `PATCH /api/cuidadores/{id}/status`, `POST /api/cuidadores/batch` e `POST /api/cuidadores/reset` (#2).
  2. **🔵 Sprint 2 (`sprint-2: mvp-core`):**
     - Suporte completo à persistência dos lotes importados via planilha CSV (`POST /api/cuidadores/batch`) e registro de cobertura de falta.
  3. **🟣 Sprint 3 (`sprint-3: evolucao` — Issues [#3](https://github.com/thomasalmeida09/escala-elderly-backend/issues/3) e [#4](https://github.com/thomasalmeida09/escala-elderly-backend/issues/4)):**
     - Endpoint de tempo real `GET /api/cuidadores/stream` via **Server-Sent Events (`SseEmitter`)** (#3) e proteção LGPD / sanitização contra *CSV Formula Injection* (#4).

---

### 🗳️ ADR-BE-006: Gestão de Tarefas via GitHub Issues + GitHub Projects (Kanban Unificado)
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
     - Separar responsabilidades em pacotes/classes claras mantendo os comentários didáticos essenciais (`ADR-BE-004`).

---

### 🗳️ ADR-BE-008: Continuous Integration (CI) & Portaria de Segurança/LGPD no Backend com GitHub Actions
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) e [`.github/ruleset-main-protection.json`](../../.github/ruleset-main-protection.json).
- **Pergunta:** *"Como vai funcionar o Continuous Integration?"*
- **Decisão:** Configurar `.github/workflows/ci.yml` (`ubuntu-24.04`, `permissions: contents: read`, Actions fixadas por SHA de 40 caracteres em runtime Node 24) executando 2 jobs obrigatórios em todo `push` e `pull_request` para a `main`:
  1. **`Java 17 Spring Boot Compile, Audit & JUnit 5 Gate` (`backend-ci`):**
     - Setup do **JDK 17 (Eclipse Temurin)** com cache do Gradle (`gradle/actions/setup-gradle@v6.4.1`) em `./escala-elderly-api`;
     - Auditoria estática *Broken-Windows* bloqueando blocos `catch {}` vazios;
     - Verificação explícita de formatação `./gradlew spotlessCheck --no-daemon` seguida de compilação e testes JUnit 5 `./gradlew check test -x spotlessCheck --no-daemon --warning-mode=all`.
  2. **`Secret, Credential & LGPD Clean-Room Hygiene Gate` (`clean-room-security`):**
     - Bloqueio determinístico de BOM UTF-8 (`0xEF 0xBB 0xBF`), arquivos `.env`, senhas hardcoded em `application.properties`, chaves privadas/tokens, PDFs de referência e vazamentos de CPF/CNPJ ou e-mails reais fora da RFC 2606 (`@example.com`).
- **Motivação & Trade-off:** Garante que nenhum commit com erro de compilação, formatação divergente, teste quebrado ou vazamento de segredos/PII LGPD possa ser mergeado na `main`.

---

### 🗳️ ADR-BE-009: Governança de Branches, Commits e Pull Requests (PRs)
- **Status:** `ACEITA / IMPLEMENTADA`
- **Onde está no código:** [`.github/pull_request_template.md`](../../.github/pull_request_template.md), [`.github/CODEOWNERS`](../../.github/CODEOWNERS) e [`.github/ruleset-main-protection.json`](../../.github/ruleset-main-protection.json).
- **Pergunta:** *"Como vão ser os PRs?"*
- **Decisão:**
  1. **Branch `main` Protegida (Ruleset):** Toda alteração é desenvolvida em branch dedicada (`fix/issue-01-...`, `feat/issue-02-...`), bloqueando *force-pushes* e exigindo histórico linear.
  2. **Conventional Commits & Fowler's Two Hats:** `feat(api): ...`, `fix(api): ...`, `docs(adr): ...`, `test(api): ...`, `ci(backend): ...`, `style(api): ...`.
  3. **Critérios para Merge:** Template de PR preenchido (`Closes #Número`) + **2 Checks da CI Verdes (`backend-ci` e `clean-room-security`)** + **1 Aprovação (Code Review roteado via `CODEOWNERS`)** + **Squash and Merge**.

---

### 🗳️ ADR-BE-010: Contrato JSON Canônico Alinhado ao Frontend e Persistência em Memória/H2
- **Decisão:**
  - Unificar os IDs e os status da API com o Frontend para evitar o bug em que clicar em *"Falta de Carlos"* alterava o registro errado:
    ```json
    [
      { "id": 1, "nome": "Ana Souza", "status": "Presente", "turno": "Diurno (07h-19h)", "quarto": "Quarto 01" },
      { "id": 2, "nome": "Lucas Lima", "status": "Presente", "turno": "Diurno (07h-19h)", "quarto": "Quarto 02" },
      { "id": 3, "nome": "Carlos Rocha", "status": "Presente", "turno": "Noturno (19h-07h)", "quarto": "Quarto 03" },
      { "id": 4, "nome": "Mariana Costa", "status": "Presente", "turno": "Noturno (19h-07h)", "quarto": "Quarto 04" }
    ]
    ```
  - Manter a persistência em um `CuidadorService` thread-safe (ou banco H2 em memória) para que a API rode instantaneamente com `./gradlew bootRun` em qualquer computador sem exigir instalação externa de banco de dados.
