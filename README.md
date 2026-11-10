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
├── docs/
│   └── adr/
│       ├── README.md                                  # Catálogo de ADRs do Backend e Governança
│       └── QUADRO-KANBAN-SPRINTS.md                   # Divisão das Issues por Sprints e Colunas
└── escala-elderly-api/
    ├── src/
    │   ├── main/java/escala_elderly_api/
    │   │   ├── EscalaElderlyApiApplication.java       # Classe de inicialização (@SpringBootApplication)
    │   │   ├── CuidadorController.java                # Endpoints REST (/api/cuidadores) com CORS
    │   │   └── Cuidador.java                          # Modelo de domínio serializado via Jackson
    │   └── test/java/escala_elderly_api/
    │       └── EscalaElderlyApiApplicationTests.java  # Suíte de testes JUnit 5
    ├── build.gradle                                   # Dependências Spring WebMVC e Java 17 Toolchain
    └── gradlew / gradlew.bat                          # Gradle Wrapper
```

---

## 🔌 Contrato de Endpoints REST

| Método | Rota | Descrição | Status |
| :---: | :--- | :--- | :---: |
| `GET` | `/api/cuidadores` | Lista todos os cuidadores e seus status atuais no turno | Implementado (V1.0) |
| `PATCH` | `/api/cuidadores/{id}/status` | Atualiza o status de presença/falta/cobertura de um cuidador | Sprint 1 ([Issue #2](https://github.com/thomasalmeida09/escala-elderly-backend/issues/2)) |
| `POST` | `/api/cuidadores/batch` | Sincroniza lote de cuidadores importados via planilha CSV | Sprint 1 ([Issue #2](https://github.com/thomasalmeida09/escala-elderly-backend/issues/2)) |
| `POST` | `/api/cuidadores/reset` | Restaura a escala inicial para demonstração | Sprint 1 ([Issue #2](https://github.com/thomasalmeida09/escala-elderly-backend/issues/2)) |
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

# 4. Executar verificação de formatação + suíte de testes automatizados (JUnit 5)
./gradlew check test
```

A API responderá em **`http://localhost:8080/api/cuidadores`**.

---

## 🤝 Fluxo de Trabalho da Equipe (Resumo das ADRs)

1. **Gestão de Tarefas:** Escolha uma Issue na coluna `🎯 Sprint 1 (To Do - Fundação)` do [GitHub Projects](https://github.com/users/augustoheiss/projects/1) e atribua seu usuário (`Assignee`).
2. **Padrão de Branches:** Crie uma branch a partir da `main` seguindo o padrão `feat/issue-XX-descricao` ou `fix/issue-XX-descricao`.
3. **Padrão de Commits:** Utilize *Conventional Commits* (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `ci:`).
4. **Pull Requests (PRs):** Abra um PR referenciando a Issue (`Closes #XX`), garanta que `./gradlew build test` está passando na **CI (GitHub Actions)** e solicite **1 Code Review** antes do *Squash and Merge*.
