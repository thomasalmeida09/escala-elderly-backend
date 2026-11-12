# ☕ Escala Elderly — Backend API (`Spring Boot 4.1 + Java 17`)

![Java](https://img.shields.io/badge/Java-17_LTS-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-Wrapper-02303A?logo=gradle&logoColor=white)
![Status](https://img.shields.io/badge/Status-Sprint_1_(Fundação)-success)

API RESTful responsável pelo gerenciamento de estado dos cuidadores, plantões e alertas operacionais do ecossistema **Escala Elderly**.

---

## 🧭 Links Rápidos de Governança & Gestão da Equipe

| Recurso de Governança | Acesso Direto | Descrição |
| :--- | :--- | :--- |
| 📊 **Quadro Kanban Interativo** | **[GitHub Projects (Front + Back)](https://github.com/users/augustoheiss/projects/1)** | Quadro unificado com o status em tempo real de todas as tarefas da equipe |
| 🖼️ **Mapa Visual da Arquitetura (PR Lens)** | **[Canvas Técnico & ADRs (`prlens.dev`)](https://prlens.dev/c/L-2rShWu_j0WpKt-jT0Cog)** | Diagramas animados de arquitetura, contrato REST (`Carlos = id 3`) e Data Flow |
| 👶 **Explicação Passo a Passo (ELI5 Canvas)** | **[Walkthrough Onboarding (`prlens.dev`)](https://prlens.dev/c/x2StISYcAmXvy49MhetrsQ)** | Tour visual progressivo de como o sistema resolve uma falta no plantão |
| 🏛️ **Catálogo de ADRs do Backend** | **[`docs/adr/README.md`](./docs/adr/README.md)** | Decisões arquiteturais já tomadas (porquê e *trade-offs*) e decisões de governança |
| 🗺️ **Divisão por Sprints e Colunas** | **[`docs/adr/QUADRO-KANBAN-SPRINTS.md`](./docs/adr/QUADRO-KANBAN-SPRINTS.md)** | Mapa completo das 14 Issues distribuídas entre as Sprints 1, 2 e 3 |
| 🏥 **Repositório Frontend (React)** | **[`escala-elderly`](https://github.com/thomasalmeida09/escala-elderly)** | Aplicação Frontend (Painel do Gerente em React 19 + TypeScript + MUI) |

### 🎬 Diagrama Interativo da Arquitetura (PR Lens)

[![Arquitetura Escala Elderly — PR Lens](https://prlens.dev/c/L-2rShWu_j0WpKt-jT0Cog.svg)](https://prlens.dev/c/L-2rShWu_j0WpKt-jT0Cog)

> 💡 *Clique no diagrama acima para abrir o **[Canvas Interativo no PR Lens](https://prlens.dev/c/L-2rShWu_j0WpKt-jT0Cog)** e inspecionar o contrato JSON de `/api/cuidadores` (ou acesse o **[Tour Visual ELI5 para Onboarding](https://prlens.dev/c/x2StISYcAmXvy49MhetrsQ)**).*

---

## 🏗️ Arquitetura e Estrutura do Repositório

```text
escala-elderly-backend/
├── .github/
│   ├── workflows/
│   │   └── ci.yml                                            # Pipeline CI Dual-Job (Spotless, JUnit, Anti-BOM & LGPD)
│   ├── CODEOWNERS                                            # Roteamento automático de Code Review (@thomasalmeida09 @augustoheiss)
│   ├── pull_request_template.md                              # Checklist de PR alinhado 1:1 às ADR-BE-001..011
│   └── ruleset-main-protection.json                          # Definição auditável de proteção da branch main
├── docs/
│   └── adr/
│       ├── README.md                                         # Catálogo de ADRs do Backend e Governança (ADR-BE-001..011)
│       └── QUADRO-KANBAN-SPRINTS.md                          # Divisão das Issues por Sprints e Colunas
├── escala-elderly-api/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/escala_elderly_api/
│   │   │   │   ├── EscalaElderlyApiApplication.java          # Classe de inicialização (@SpringBootApplication)
│   │   │   │   ├── WebApiConfig.java                         # CorsFilter em /api/** e customizador estrito Jackson 3
│   │   │   │   ├── CuidadorController.java                   # Rotas REST (GET, PATCH /status, POST /batch, POST /reset)
│   │   │   │   ├── CuidadorService.java                      # Motor MVCC lock-free (AtomicReference<List<Cuidador>>)
│   │   │   │   ├── Cuidador.java                             # Entidade imutável canônica (@JsonInclude NON_NULL)
│   │   │   │   ├── StatusCuidador.java                       # Política dos 4 status válidos (Presente, Faltou, Folga, Cobertura)
│   │   │   │   ├── CsvSanitizer.java                         # Higienização em ponto fixo anti-CSV Injection e telefone E.164
│   │   │   │   ├── LgpdPrivacyMasker.java                    # Pseudonimização read-time PT-BR e máscara de contato LGPD
│   │   │   │   ├── StatusUpdateRequest.java                  # Record DTO para PATCH /api/cuidadores/{id}/status
│   │   │   │   ├── CuidadorBatchItem.java                    # Record DTO para POST /api/cuidadores/batch
│   │   │   │   ├── CuidadorNotFoundException.java            # Exceção de domínio 404 (CUIDADOR_NOT_FOUND)
│   │   │   │   ├── InvalidCuidadorPayloadException.java      # Exceção de domínio 400 (INVALID_PAYLOAD / STATUS / ID)
│   │   │   │   ├── ApiExceptionHandler.java                  # @RestControllerAdvice uniforme (400/404/405/415/500)
│   │   │   │   └── ApiErrorResponse.java                     # Record padronizado de erro HTTP (code, message, path, timestamp)
│   │   │   └── resources/
│   │   │       └── application.properties                    # Configuração base da aplicação Spring Boot
│   │   └── test/
│   │       └── java/escala_elderly_api/
│   │           ├── EscalaElderlyApiApplicationTests.java     # Testes integrados MockMvc (Contrato, PATCH/POST, Erros & CORS)
│   │           ├── CuidadorServiceTest.java                  # Testes unitários de estado, lotes e barreira CyclicBarrier (24 threads)
│   │           ├── SecurityAndLgpdPolicyTest.java            # Testes unitários de CsvSanitizer (OWASP/E.164) e LgpdPrivacyMasker
│   │           └── GovernanceHygieneTest.java                # Fitness Functions (paridade README 1:1, ADRs, CI & Clean-Room LGPD)
│   ├── build.gradle                                          # Spring WebMVC, Java 17 Toolchain & Spotless (AOSP)
│   ├── settings.gradle                                       # Definição do módulo Gradle escala-elderly-api
│   └── gradlew / gradlew.bat                                 # Gradle Wrapper (9.5.1)
├── .editorconfig                                             # Padrão de indentação, UTF-8 sem BOM e LF
├── .gitattributes                                            # Normalização determinística de quebras de linha (LF)
├── .gitignore                                                # Exclusão de binários, cache Gradle e arquivos de IDE
└── README.md                                                 # Documentação principal, árvore arquitetural e guia rápido
```

---

## 🔌 Contrato de Endpoints REST

| Método | Rota | Descrição | Status |
| :---: | :--- | :--- | :---: |
| `GET` | `/api/cuidadores` | Lista cuidadores alinhados ao Frontend (`id: 3 = Carlos Rocha`) ou projeção LGPD (`?privacy=true`) | Implementado ([Issue #2](https://github.com/thomasalmeida09/escala-elderly-backend/issues/2) & [Issue #4](https://github.com/thomasalmeida09/escala-elderly-backend/issues/4)) |
| `PATCH` | `/api/cuidadores/{id}/status` | Atualiza status estrito (`Presente`, `Faltou`, `Folga`, `Cobertura`) e higieniza `substituto` | Implementado ([Issue #2](https://github.com/thomasalmeida09/escala-elderly-backend/issues/2) & [Issue #4](https://github.com/thomasalmeida09/escala-elderly-backend/issues/4)) |
| `POST` | `/api/cuidadores/batch` | Sincroniza atomicamente lote CSV com sanitização OWASP anti-CSV Injection e telefone E.164/DDD | Implementado ([Issue #2](https://github.com/thomasalmeida09/escala-elderly-backend/issues/2) & [Issue #4](https://github.com/thomasalmeida09/escala-elderly-backend/issues/4)) |
| `POST` | `/api/cuidadores/reset` | Restaura instantaneamente a escala inicial canônica de demonstração | Implementado (Sprint 1 — [Issue #2](https://github.com/thomasalmeida09/escala-elderly-backend/issues/2)) |
| `GET` | `/api/cuidadores/stream` | Stream de atualização em tempo real via Server-Sent Events (SSE) | Sprint 3 ([Issue #3](https://github.com/thomasalmeida09/escala-elderly-backend/issues/3)) |

---

## 🚀 Como Executar a API Localmente

### Pré-requisitos
- **JDK 17** instalado (ou gerenciado automaticamente pelo Gradle Toolchain).

### Passo a passo

```bash
# 1. Entrar na pasta do projeto Spring Boot
cd escala-elderly-api

# 2. Executar a aplicação na porta 8080 (Linux/macOS/Git Bash)
./gradlew bootRun

# Ou no Windows PowerShell/CMD:
.\gradlew.bat bootRun

# 3. Padronizar formatação automática Java (Spotless / Google Java Format AOSP) antes de commitar
./gradlew spotlessApply
# Ou no Windows PowerShell/CMD:
.\gradlew.bat spotlessApply

# 4. Executar verificação de formatação + suíte de testes automatizados (JUnit)
./gradlew check test
```

A API responderá em **`http://localhost:8080/api/cuidadores`**. Exemplos rápidos de teste via `curl`:

```bash
# Listar escala ativa (visão completa de gestão)
curl -s http://localhost:8080/api/cuidadores

# Listar escala em Modo Mural Público LGPD (sobrenomes abreviados e telefones mascarados — ADR-BE-011)
curl -s "http://localhost:8080/api/cuidadores?privacy=true"

# Registrar falta de Carlos Rocha (id = 3)
curl -s -X PATCH http://localhost:8080/api/cuidadores/3/status \
  -H "Content-Type: application/json" \
  -d '{"status":"Faltou"}'

# Alocar cobertura no posto de Carlos Rocha
curl -s -X PATCH http://localhost:8080/api/cuidadores/3/status \
  -H "Content-Type: application/json" \
  -d '{"status":"Cobertura","substituto":"Folguista Turno A"}'

# Restaurar os 4 cuidadores iniciais em 1 clique
curl -s -X POST http://localhost:8080/api/cuidadores/reset
```

> 🔒 **Configuração de CORS em Produção (`ADR-BE-001` & `ADR-BE-011`):** Por padrão, `escala.cors.allowed-origins=*` em `escala-elderly-api/src/main/resources/application.properties` facilita o desenvolvimento local. Em produção/apresentação, defina origens explícitas separadas por vírgula (ex.: `escala.cors.allowed-origins=http://localhost:5173,https://escala.example.com`).

---

## 🤝 Fluxo de Trabalho da Equipe (Resumo das ADRs)

1. **Gestão de Tarefas:** Escolha uma Issue na coluna `🎯 Sprint 1 (To Do - Fundação)` do [GitHub Projects](https://github.com/users/augustoheiss/projects/1) e atribua seu usuário (`Assignee`).
2. **Padrão de Branches:** Crie uma branch a partir da `main` seguindo o padrão `feat/issue-XX-descricao` ou `fix/issue-XX-descricao`.
3. **Padrão de Commits:** Utilize *Conventional Commits* (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `ci:`) sob a disciplina *Fowler's Two Hats* (`ADR-BE-009`).
4. **Pull Requests (PRs):** Abra um PR referenciando a Issue (`Closes #XX`), garanta que `./gradlew check test` está passando na **CI (GitHub Actions)** (`ADR-BE-008`) e solicite **1 Code Review** antes do *Squash and Merge*.
