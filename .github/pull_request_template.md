## 🎯 Objetivo deste Pull Request

<!-- Descreva de forma clara e direta O QUE mudou e POR QUÊ essa decisão foi tomada -->

- **Issue vinculada:** `Closes #`
- **Tipo de mudança (`Fowler's Two Hats`):**
  - [ ] `feat:` Nova funcionalidade / endpoint (mudança de comportamento)
  - [ ] `fix:` Correção de bug (mudança de comportamento)
  - [ ] `refactor:` Refatoração estrutural pura (sem alterar contrato da API)
  - [ ] `chore:` / `docs:` / `test:` Infraestrutura, documentação ou testes

---

## 🛡️ Checklist Automatizado de Engenharia & Governança

### 1. Qualidade & Arquitetura Backend (`Java 17 / Spring Boot` & `ADRs`)
- [ ] **Compilação & Testes (`./gradlew check test`):** Projeto compila sem erros e passa na suíte JUnit 5.
- [ ] **Contrato de Dados (`ADR-008`):** Se o JSON do endpoint `/api/cuidadores` mudou, o contrato com o Frontend (`escala-elderly`) está documentado e sincronizado.
- [ ] **Semântica HTTP (`ADR-010`):** Endpoints retornam códigos HTTP apropriados (`200 OK`, `201 Created`, `404 Not Found`, `400 Bad Request`).

### 2. Pragmatic Code Review & Git Hygiene (`agency-code-reviewer` & `agency-git-workflow-master`)
- [ ] **Zero Broken Windows:** Sem blocos `catch {}` vazios que engolem exceções silenciosamente (*Dead Programs Tell No Lies*).
- [ ] **Commits Atômicos:** Cada commit tem responsabilidade única e pode ser revertido com segurança.

### 3. Segurança & Higiene de Dados LGPD (`agency-clean-room-data-hygiene-engineer` & `agency-secrets-credential-hygiene-engineer`)
- [ ] **Zero Credenciais no Código:** Nenhuma senha de banco (`spring.datasource.password`), chave de API ou `.env` foi comitado em `application.properties`.
- [ ] **Clean-Room & Zero PII:** Nenhum CPF real, telefone real ou dado pessoal sensível de pacientes/cuidadores foi incluído em seeds, controllers ou testes.

---

## 🧪 Como Testar / Exemplo de Request & Response

<!-- Exemplo de chamada curl / Postman ou saída do teste automatizado -->
