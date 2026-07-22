# Plano: Autenticação por API key (header X-API-Key)

## Causa raiz do problema

O `spring-boot-starter-security` está no `pom.xml` sem nenhuma `SecurityConfiguration`. O Spring Boot Security default bloqueia todos os endpoints com Basic auth (usuário `user` + senha gerada a cada boot). O Bruno envia `auth: none` e recebe `401`/`302` (redirect para `/login`), nunca chegando ao controller. Os testes atuais passam porque usam `@AutoConfigureMockMvc(addFilters = false)`, que desvia da security.

## Decisões (confirmadas)

- **Padrão**: header `X-API-Key: <chave>`, validado por filtro customizado.
- **Sem default**: se `marmore.api.key` não estiver definida, a API falha na inicialização (não sobe insegura).
- **CSRF desabilitado**: API stateless por header, sem cookies de sessão.

## Arquitetura

```
Request → [ApiKeyAuthFilter] → [AuthorizationFilter] → Controller
            │  lê X-API-Key
            │  compara com marmore.api.key (MessageDigest.isEqual, anti-timing)
            ├─ válida: seta Authentication autenticada, continua chain
            └─ ausente/inválida: 401 JSON, NÃO continua chain
```

- **`ApiKeyAuthFilter`** (`OncePerRequestFilter`): se a chave configurada estiver ausente em runtime, lança `IllegalStateException` (não deve acontecer pois o properties valida na inicialização). Compara com `MessageDigest.isEqual` (constante no tempo, evita timing attack). Em ausência/invalidade, escreve resposta 401 JSON e não chama `chain.doFilter`.
- **`SecurityConfiguration`**: `@Configuration` com `SecurityFilterChain` bean. `csrf.disable()`, `authorizeHttpRequests` com `anyRequest().authenticated()`, `addFilterBefore(apiKeyFilter, AuthorizationFilter)`, `sessionManagement.stateless`. Desabilita form login e Basic auth default (não queremos a senha gerada).

## Arquivos

**Novos (main):**
- `src/main/java/com/marmore/api/security/ApiKeyProperties.java` — `@ConfigurationProperties(prefix = "marmore.api")`, campo `key`. `@Validated` ou validação no construtor: se `key` for blank, falha na inicialização.
- `src/main/java/com/marmore/api/security/ApiKeyAuthFilter.java` — `OncePerRequestFilter`, valida `X-API-Key`.
- `src/main/java/com/marmore/api/security/SecurityConfiguration.java` — `SecurityFilterChain` bean (Lambda DSL, Spring Security 7).

**Novos (test):**
- `src/test/java/com/marmore/api/security/ApiKeyAuthFilterTest.java` — casos: chave correta passa, chave errada 401, header ausente 401, timing-safe.
- `src/test/java/com/marmore/api/security/SecurityConfigurationTest.java` — integração `MockMvc`: sem header → 401, com header válido → chega ao controller (200/4xx de negócio), chave default ausente → contexto não sobe.

**Alterados:**
- `src/main/resources/application.yaml` — adiciona `marmore.api.key: ${MARMORE_API_KEY:}`.
- `.env` — adiciona `MARMORE_API_KEY=<chave-local>` (o Bruno precisará enviar a mesma).
- `src/test/java/com/marmore/api/image/web/ImageEditControllerTest.java` — remove `addFilters = false` (não queremos bypassar a security real nos testes do controller); injeta a API key válida no header dos requests para que cheguem ao controller. Isso garante que o teste valida o caminho real.
- `bruno/marmore-api/editar_imagem.bru` — adiciona header `X-API-Key: {{api_key}}`.
- `bruno/marmore-api/environments/Local.bru` — adiciona `api_key: <chave-local>`.

## Detalhes de implementação

### `ApiKeyProperties`
```java
@ConfigurationProperties(prefix = "marmore.api")
public class ApiKeyProperties {
  private String key;  // getter/setter
}
```
Validação: o `SecurityConfiguration` (ou o filtro) verifica na inicialização que a chave não é blank. Se for, `throw new IllegalStateException` impede o contexto de subir. Implementação mais limpa: um `@PostConstruct` no `ApiKeyAuthFilter` ou validação no `SecurityConfiguration` bean.

### `ApiKeyAuthFilter` (esqueleto)
```java
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
  static final String HEADER = "X-API-Key";
  private final String expectedKey;

  @Override
  protected void doFilterInternal(req, res, chain) {
    String provided = req.getHeader(HEADER);
    if (provided != null && MessageDigest.isEqual(
        provided.getBytes(UTF_8), expectedKey.getBytes(UTF_8))) {
      // autentica: seta Authentication autenticada no SecurityContext
      SecurityContextHolder.getContext().setAuthentication(
          new TestingAuthenticationToken("apikey", null, "ROLE_API"));  // ou PreAuth
      chain.doFilter(req, res);
    } else {
      res.setStatus(401);
      res.setContentType("application/json");
      res.getWriter().write("{\"error\":\"API key ausente ou invalida\"}");
    }
  }
}
```
Nota sobre o tipo de `Authentication`: usarei um `Authentication` simples autenticado (não `PreAuthenticatedAuthenticationToken`, que é over-engineering para chave estática). Uma opção minimal é criar um `AbstractAuthenticationToken` de 1 uso, ou usar `UsernamePasswordAuthenticationToken.authenticated("apikey", null, List.of())`. Vou pelo mais simples que satisfaça `anyRequest().authenticated()`.

### `SecurityConfiguration` (Lambda DSL, Spring Security 7)
```java
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, ApiKeyAuthFilter apiKeyFilter) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
      .authorizeHttpRequests(a -> a.anyRequest().authenticated())
      .addFilterBefore(apiKeyFilter, AuthorizationFilter.class)
      .httpBasic(b -> b.disable())   // remove o Basic default e a senha gerada
      .formLogin(f -> f.disable());   // remove redirect p/ login
    return http.build();
  }
}
```
O `httpBasic.disable()` e `formLogin.disable()` eliminam a senha gerada no log e o redirect para `/login`.

## Tarefas (TDD, baby steps)

1. **`ApiKeyProperties` + validação na inicialização** + teste (contexto não sobe sem chave). → verify: teste RED depois GREEN.
2. **`ApiKeyAuthFilter`** + testes de unidade (chave certa/errada/ausente, timing-safe). → verify: RED → GREEN.
3. **`SecurityConfiguration`** + teste de integração (`MockMvc`: sem header 401, com header chega ao controller). → verify: RED → GREEN.
4. **Ajustar `ImageEditControllerTest`**: remover `addFilters = false`, adicionar header `X-API-Key` nos requests. Confirmar que continua verde (valida caminho real, não bypass). → verify: GREEN.
5. **`application.yaml` + `.env`**: adicionar `marmore.api.key`.
6. **Bruno**: adicionar header `X-API-Key` no `.bru` e `api_key` no `Local.bru`.
7. **`make format && make verify`** → verify: BUILD SUCCESS, 0 violações.
8. **Smoke test manual**: subir app, reproduzir o request do Bruno (com e sem header) via `curl`, confirmar 401 sem header e 200/502/503 com header.
9. Commits por task (Conventional Commits).

## Fora de escopo (YAGNI)

- Rate limiting por API key.
- Múltiplas chaves / rotação.
- Roles/autorização granular (só `authenticated()`).
- Persistência de chaves em banco.
- Autenticação via JWT/OAuth (a pedido futuro).
- HTTPS/redirect (responsabilidade do reverse proxy em produção).

## Riscos

- **Quebrar outros endpoints**: `anyRequest().authenticated()` protege tudo. Se houver endpoint público no futuro, precisa `permitAll` explícito. Hoje só existe `/images/edit`, então é seguro.
- **Testes do controller quebram**: ao remover `addFilters = false`, todo teste que não enviar o header falha com 401. Mitigação: injetar a chave no `@TestPropertySource` e enviar o header nos requests. Vou revisar todos os testes `@SpringBootTest` com `MockMvc`.
- **Default de security do Spring Boot**: depois da `SecurityConfiguration`, o `UserDetailsServiceAutoConfiguration` (que gera a senha) ainda pode tentar rodar. O `httpBasic.disable()` + `formLogin.disable()` neutraliza; se persistir, excluir o autoconfig.

## Verificação de aceitação (contrato)

- `curl POST /images/edit` **sem** `X-API-Key` → `401` JSON `{"error":"..."}`.
- `curl POST /images/edit` **com** `X-API-Key` inválido → `401`.
- `curl POST /images/edit` **com** `X-API-Key` válido → chega ao controller (200/4xx/5xx de negócio).
- Log de boot **não** exibe mais "generated security password".
- App **não sobe** se `MARMORE_API_KEY` estiver ausente.
- Bruno `editar_imagem.bru` com `X-API-Key` → chega ao controller (não mais 401/302).

Qual abordagem de execução? Inline nesta sessão com checkpoints por task.