## 🎯 Objetivo deste Pull Request

<!-- Descreva de forma clara e direta O QUE mudou e POR QUÊ essa decisão foi tomada -->

- **Issue vinculada:** `Closes #`
- **Tipo de mudança (`Fowler's Two Hats` — `ADR-BE-009`):**
  - [ ] `feat:` Nova funcionalidade / endpoint (mudança de comportamento)
  - [ ] `fix:` Correção de bug (mudança de comportamento)
  - [ ] `refactor:` Refatoração estrutural pura (sem alterar contrato da API)
  - [ ] `chore:` / `docs:` / `test:` / `ci:` / `style:` Infraestrutura, formatação, documentação ou testes

---

## 🛡️ Checklist Automatizado de Engenharia & Governança

### 1. Qualidade, Formatação & Arquitetura Backend (`Java 17 / Spring Boot 4.1` & `ADRs`)
- [ ] **Formatação Determinística (`ADR-BE-007`):** `./gradlew spotlessApply` (`.\gradlew.bat spotlessApply` no Windows) executado em `./escala-elderly-api` (Google Java Format AOSP 4 espaços, `LF`, UTF-8 sem BOM).
- [ ] **Compilação & Testes (`ADR-BE-002` & `ADR-BE-008`):** `./gradlew check test --no-daemon` passa 100% verde na suíte JUnit 5 sem warnings.
- [ ] **Contrato JSON Canônico (`ADR-BE-010`):** Eventuais mudanças no payload de `/api/cuidadores` mantêm paridade documentada e sincronizada com o Frontend (`escala-elderly`).
- [ ] **Arquitetura REST, Semântica HTTP & CORS (`ADR-BE-001`):** Endpoints retornam códigos HTTP apropriados (`200 OK`, `201 Created`, `400 Bad Request`, `404 Not Found`) com CORS compatível com `localhost:5173`.

### 2. Pragmatic Code Review, Didática & Git Hygiene (`ADR-BE-004` & `ADR-BE-009`)
- [ ] **Dignidade Didática & Zero Broken Windows (`ADR-BE-004`):** Comentários pedagógicos relevantes preservados e zero blocos `catch {}` vazios que engolem exceções silenciosamente (*Dead Programs Tell No Lies*).
- [ ] **Commits Atômicos (`ADR-BE-009`):** Cada commit tem responsabilidade única (*Conventional Commits*) e pode ser revertido com segurança.

### 3. Segurança & Higiene de Dados LGPD (`clean-room-security`)
- [ ] **Zero Credenciais no Código:** Nenhuma senha de banco (`spring.datasource.password`), chave de API ou `.env` foi comitado em `application.properties`.
- [ ] **Clean-Room & Zero PII:** Nenhum CPF/CNPJ real, e-mail real fora da RFC 2606 (`@example.com`), PDF de referência ou dado pessoal sensível de pacientes/cuidadores foi incluído em seeds, controllers ou testes.

---

## 🧪 Como Testar / Exemplo de Request & Response

<!-- Exemplo de chamada curl / Postman ou saída do teste automatizado -->
