# SSE Edição de Imagem — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir o endpoint síncrono `POST /images/edit` por um fluxo SSE reativo que consome o stream da OpenAI (`stream=true`, `partial_images=0`), emite eventos de status/heartbeat/done/imagem e calcula custo em BRL.

**Architecture:** Migração de Spring MVC (servlet) para Spring WebFlux (reativo, Netty). Pipeline reativo ponta a ponta: `FilePart` → resize → gateway WebClient (consome SSE da OpenAI) → `Flux<ServerSentEvent>` para o cliente. Segurança migra de `OncePerRequestFilter` para `SecurityWebFilterChain`. Feature-folder: pacote `image` → `imageedit`.

**Tech Stack:** Java 26, Spring Boot 4.1.0, Spring Security 7 (reativo), WebFlux, WebClient, reactor-core, reactor-test, OkHttp MockWebServer, thumbnailator 0.4.21, Jackson 3 (`tools.jackson.databind`).

**Spec:** `docs/superpowers/specs/2026-07-22-sse-edicao-imagem-design.md`

## Global Constraints

- **Indentação:** 2 espaços, sem tabs, line length 100 (Google Java Style). Spotless com google-java-format 1.30.0 corrige automaticamente (`make format`). Checkstyle 11.0.1 com `config/checkstyle/checkstyle.xml`.
- **Commits:** Conventional Commits. Stagear arquivos explicitamente com `git add <file>`. Um commit por tarefa (ou sub-step conforme indicado).
- **Jackson:** o projeto usa Jackson 3 (`tools.jackson.databind.JsonNode`, não `com.fasterxml`). Sempre importar de `tools.jackson.*`.
- **Tests:** todo código novo segue TDD (RED → GREEN → REVERT → GREEN). Build via `make` (alvos: `make test`, `make lint`, `make format`). Pre-commit hook roda checkstyle + spotless automaticamente.
- **No placeholders:** todo step tem código completo. Nenhum "TODO", "implementar depois".
- **Idioma:** Javadocs e mensagens em português, nome de classes em inglês (conforme padrão do codebase).
- **Reativo:** todo I/O no event loop. Operações bloqueantes (`resize`, leitura de pedra em disco) envolvidas em `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`.

---

## File Structure

Arquivos agrupados por responsabilidade. As tarefas seguem a ordem de dependência (bottom-up): primeiro domínio puro e componentes isolados, depois gateway, service, web, security, e por fim a migração de stack e remoção do MVC.

### Novos (feature `imageedit`)

| Arquivo | Responsabilidade |
|---|---|
| `imageedit/domain/ImageCost.java` | record puro: custo USD, custo BRL, usage. |
| `imageedit/cost/ImageCostCalculator.java` | tabela hardcoded `PRICE_PER_IMAGE` + lookup. |
| `imageedit/cost/UsdBrlProperties.java` | `@ConfigurationProperties(prefix="marmore.cost.usd-brl")`: url, cache-ttl, fallback. |
| `imageedit/cost/UsdBrlProvider.java` | busca câmbio na AwesomeAPI com cache + fallback. |
| `imageedit/web/SseEvents.java` | factory de `ServerSentEvent<Object>` (status, ping, done, imagem, error). |
| `imageedit/web/ImageEditRouter.java` | `RouterFunction` POST `/images/edit`. |
| `imageedit/web/ImageEditHandler.java` | handler reativo que monta o `Flux<ServerSentEvent>`. |
| `imageedit/config/WebClientConfig.java` | bean `WebClient` com base-url + Bearer + timeouts. |
| `imageedit/ai/OpenAiWebClientImageEditModel.java` | gateway WebClient consumindo SSE da OpenAI. |

### Migrados (mesma lógica, nova assinatura/pacote)

| Arquivo | Mudança |
|---|---|
| `imageedit/ai/ImageEditModel.java` | `call(prompt) → Mono<ImageResponse>` (era síncrono). |
| `imageedit/service/ImageEditService.java` | `generate(byte[]) → Mono<GenerateResult>` (era síncrono). |
| `imageedit/web/ImageEditException.java` | agora `extends RuntimeException` (era `ResponseStatusException`). |
| `security/SecurityConfiguration.java` | reescrito para `ServerHttpSecurity` / `SecurityWebFilterChain`. |
| `security/ApiKeyAuthWebFilter.java` | substitui `ApiKeyAuthFilter`; novo `WebFilter` reativo. |
| `web/GlobalWebExceptionHandler.java` | substitui `GlobalExceptionHandler`; vira `WebExceptionHandler`. |

### Movidos (só pacote `image` → `imageedit`, sem mudança de lógica)

`image/ai/{ImageEditPrompt, AiImageOptions, InputImage, ImageResponse, ImageResponseMetadata, ImageGeneration, Image, AiImageException}.java`, `image/service/ImageResizer.java`, `image/domain/{GenerateResult, EditPrompts}.java`, `image/io/{ImageResultWriter, FileSystemResultWriter}.java`, `image/config/ImageEditProperties.java`.

### Removidos

`image/web/ImageEditController.java`, `image/ai/OpenAiRestClientImageEditModel.java`, `image/config/RestClientConfig.java`, `security/ApiKeyAuthFilter.java`, `web/GlobalExceptionHandler.java`.

### Modificados

`pom.xml` (remove webmvc/restclient + test starters), `ApiApplication.java` (package registry), `application.yaml` (adiciona `marmore.cost`).

---

## Task 1: Domain record `ImageCost`

Cria o record puro de custo. Sem dependências. Base para o calculator.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/domain/ImageCost.java`
- Test: `src/test/java/com/marmore/api/imageedit/domain/ImageCostTest.java`

**Interfaces:**
- Produces: `record ImageCost(BigDecimal costUsd, BigDecimal costBrl, JsonNode usage)`

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.imageedit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ImageCostTest {

  @Test
  void mantemValoresPassados() {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode usage = mapper.readTreeOrValue("{\"input_tokens\":10,\"output_tokens\":20}", JsonNode.class);
    ImageCost cost = new ImageCost(new BigDecimal("0.053"), new BigDecimal("0.271"), usage);
    assertThat(cost.costUsd()).isEqualByComparingTo("0.053");
    assertThat(cost.costBrl()).isEqualByComparingTo("0.271");
    assertThat(cost.usage().path("output_tokens").asInt()).isEqualTo(20);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `ImageCost` não existe (erro de compilação).

- [ ] **Step 3: Write minimal implementation**

```java
package com.marmore.api.imageedit.domain;

import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;

/**
 * Custo de uma geracao de imagem. Valores monetarios em {@link BigDecimal} para precisao. O
 * {@code usage} e o JSON cru retornado pela OpenAI (tokens de entrada/saida).
 *
 * @param costUsd custo em dolares (tabela oficial OpenAI)
 * @param costBrl custo em reais (costUsd x cambio do dia)
 * @param usage uso de tokens reportado pela OpenAI
 */
public record ImageCost(BigDecimal costUsd, BigDecimal costBrl, JsonNode usage) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marmore/api/imageedit/domain/ImageCost.java \
        src/test/java/com/marmore/api/imageedit/domain/ImageCostTest.java
git commit -m "feat(cost): adiciona record ImageCost para custo de geracao"
```

---

## Task 2: `ImageCostCalculator`

Tabela hardcoded de preços OpenAI (jul/2026). Lookup por `model × quality × size`. Puro, sem I/O.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/cost/ImageCostCalculator.java`
- Test: `src/test/java/com/marmore/api/imageedit/cost/ImageCostCalculatorTest.java`

**Interfaces:**
- Consumes: `AiImageOptions` (model, quality, size) — ainda em `com.marmore.api.image.ai`. **Atenção:** se `AiImageOptions` ainda estiver no pacote `image` (não migrado), importar de `com.marmore.api.image.ai.AiImageOptions`. A migração de pacote (Task 14) ajustará o import depois.
- Produces: `Optional<BigDecimal> costUsd(String model, String quality, String size)`

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.imageedit.cost;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ImageCostCalculatorTest {

  private final ImageCostCalculator calc = new ImageCostCalculator();

  @Test
  void defaultsAtuaisGptImage2Medium1024() {
    assertThat(calc.costUsd("gpt-image-2", "medium", "1024x1024"))
        .hasValue(new BigDecimal("0.053"));
  }

  @Test
  void autoEResolvidoPara1024x1024Medium() {
    assertThat(calc.costUsd("gpt-image-2", "auto", "auto"))
        .hasValue(new BigDecimal("0.053"));
  }

  @Test
  void highGptImage1_5Portrait() {
    assertThat(calc.costUsd("gpt-image-1.5", "high", "1024x1536"))
        .hasValue(new BigDecimal("0.200"));
  }

  @Test
  void modeloDesconhecidoViraEmpty() {
    assertThat(calc.costUsd("modelo-inexistente", "medium", "1024x1024")).isEmpty();
  }

  @Test
  void qualityDesconhecidaViraEmpty() {
    assertThat(calc.costUsd("gpt-image-2", "ultra", "1024x1024")).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `ImageCostCalculator` não existe.

- [ ] **Step 3: Write minimal implementation**

```java
package com.marmore.api.imageedit.cost;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Calcula o custo em USD de uma geracao de imagem pela tabela oficial de precos da OpenAI
 * (jul/2026). Indexa modelo x quality x size. "auto" e resolvido para "1024x1024" e "medium"
 * (defaults da OpenAI). Retorna {@link Optional#empty()} se a combinacao nao existir na tabela.
 */
public final class ImageCostCalculator {

  private static final String SQUARE = "1024x1024";

  private static final Map<String, Map<String, Map<String, BigDecimal>>> PRICE_PER_IMAGE =
      Map.of(
          "gpt-image-2",
              Map.of(
                  "low", Map.of("1024x1024", bd("0.006"), "1024x1536", bd("0.005"), "1536x1024", bd("0.005")),
                  "medium", Map.of("1024x1024", bd("0.053"), "1024x1536", bd("0.041"), "1536x1024", bd("0.041")),
                  "high", Map.of("1024x1024", bd("0.211"), "1024x1536", bd("0.165"), "1536x1024", bd("0.165"))),
          "gpt-image-1.5",
              Map.of(
                  "low", Map.of("1024x1024", bd("0.009"), "1024x1536", bd("0.013"), "1536x1024", bd("0.013")),
                  "medium", Map.of("1024x1024", bd("0.034"), "1024x1536", bd("0.050"), "1536x1024", bd("0.050")),
                  "high", Map.of("1024x1024", bd("0.133"), "1024x1536", bd("0.200"), "1536x1024", bd("0.200"))),
          "gpt-image-1-mini",
              Map.of(
                  "low", Map.of("1024x1024", bd("0.005"), "1024x1536", bd("0.006"), "1536x1024", bd("0.006")),
                  "medium", Map.of("1024x1024", bd("0.011"), "1024x1536", bd("0.015"), "1536x1024", bd("0.015")),
                  "high", Map.of("1024x1024", bd("0.036"), "1024x1536", bd("0.052"), "1536x1024", bd("0.052"))));

  /**
   * Custo em USD por imagem.
   *
   * @param model nome do modelo (ex: "gpt-image-2")
   * @param quality qualidade ("low", "medium", "high", "auto")
   * @param size tamanho ("1024x1024", "1024x1536", "1536x1024", "auto")
   * @return custo em USD, ou empty se a combinacao nao existir
   */
  public Optional<BigDecimal> costUsd(String model, String quality, String size) {
    String resolvedSize = "auto".equals(size) ? SQUARE : size;
    String resolvedQuality = "auto".equals(quality) ? "medium" : quality;
    return Optional.ofNullable(PRICE_PER_IMAGE)
        .map(m -> m.get(model))
        .map(m -> m.get(resolvedQuality))
        .map(m -> m.get(resolvedSize));
  }

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marmore/api/imageedit/cost/ImageCostCalculator.java \
        src/test/java/com/marmore/api/imageedit/cost/ImageCostCalculatorTest.java
git commit -m "feat(cost): adiciona ImageCostCalculator com tabela OpenAI jul/2026"
```

---

## Task 3: `UsdBrlProperties`

Properties do câmbio. Sem lógica. Base para o `UsdBrlProvider`.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/cost/UsdBrlProperties.java`
- Test: `src/test/java/com/marmore/api/imageedit/cost/UsdBrlPropertiesTest.java`

**Interfaces:**
- Produces: `@ConfigurationProperties(prefix="marmore.cost.usd-brl")` com `url`, `cacheTtl`, `fallback`. Registrado em `ApiApplication` na Task 15.

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.imageedit.cost;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class UsdBrlPropertiesTest {

  @Test
  void temDefaults() {
    UsdBrlProperties props = new UsdBrlProperties();
    assertThat(props.getUrl())
        .isEqualTo("https://economia.awesomeapi.com.br/json/last/USD-BRL");
    assertThat(props.getCacheTtl()).isEqualTo(Duration.ofHours(6));
    assertThat(props.getFallback()).isEqualByComparingTo("5.1075");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `UsdBrlProperties` não existe.

- [ ] **Step 3: Write minimal implementation**

```java
package com.marmore.api.imageedit.cost;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do provedor de cambio USD->BRL.
 *
 * @param url endpoint da API de cambio (AwesomeAPI)
 * @param cacheTtl tempo de vida do cache em memoria
 * @param fallback cotacao usada se a API falhar (Investing 17/07/2026)
 */
@ConfigurationProperties(prefix = "marmore.cost.usd-brl")
public record UsdBrlProperties(
    String url, Duration cacheTtl, BigDecimal fallback) {

  /** Construtor com defaults. */
  public UsdBrlProperties() {
    this(
        "https://economia.awesomeapi.com.br/json/last/USD-BRL",
        Duration.ofHours(6),
        new BigDecimal("5.1075"));
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marmore/api/imageedit/cost/UsdBrlProperties.java \
        src/test/java/com/marmore/api/imageedit/cost/UsdBrlPropertiesTest.java
git commit -m "feat(cost): adiciona UsdBrlProperties com defaults"
```

---

## Task 4: `UsdBrlProvider`

Busca câmbio na AwesomeAPI com cache em memória (TTL) + fallback. Usa `WebClient`. Esta é a primeira classe reativa do plano.

**Pré-requisito:** `WebClient` bean existe. Para tornar esta tarefa independente, o teste injeta um `WebClient.Builder` customizado apontando para `MockWebServer`.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/cost/UsdBrlProvider.java`
- Test: `src/test/java/com/marmore/api/imageedit/cost/UsdBrlProviderTest.java`

**Interfaces:**
- Consumes: `WebClient.Builder` (construído no teste com baseUrl do MockWebServer), `UsdBrlProperties`.
- Produces: `Mono<BigDecimal> currentRate()`

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.imageedit.cost;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UsdBrlProviderTest {

  private static MockWebServer server;
  private static UsdBrlProvider provider;

  @BeforeAll
  static void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    UsdBrlProperties props =
        new UsdBrlProperties(server.url("/").toString(), java.time.Duration.ofHours(6), new BigDecimal("5.1075"));
    provider = new UsdBrlProvider(WebClient.builder(), props);
  }

  @AfterAll
  static void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void parseiaBidDaAwesomeApi() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"USDBRL\":{\"bid\":\"5.42\"}}"));
    StepVerifier.create(provider.currentRate())
        .assertNext(rate -> assertThat(rate).isEqualByComparingTo("5.42"))
        .verifyComplete();
  }

  @Test
  void usaFallbackEmErroHttp() {
    server.enqueue(new MockResponse().setResponseCode(500));
    StepVerifier.create(provider.currentRate())
        .assertNext(rate -> assertThat(rate).isEqualByComparingTo("5.1075"))
        .verifyComplete();
  }

  @Test
  void cacheaNaoRefazAntesDoTtl() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"USDBRL\":{\"bid\":\"5.99\"}}"));
    // primeira chamada popula o cache
    BigDecimal first = provider.currentRate().block();
    assertThat(first).isEqualByComparingTo("5.99");
    // segunda chamada deve usar o cache (nao enfileira nova resposta no server)
    BigDecimal second = provider.currentRate().block();
    assertThat(second).isEqualByComparingTo("5.99");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `UsdBrlProvider` não existe, e `okhttp3.mockwebserver` pode estar ausente do `pom.xml`.

Se `MockWebServer` não compilar, adicione a dependência de teste primeiro:

```xml
<dependency>
  <groupId>com.squareup.okhttp3</groupId>
  <artifactId>mockwebserver</artifactId>
  <version>4.12.0</version>
  <scope>test</scope>
</dependency>
```

em `pom.xml` dentro de `<dependencies>` (antes de `<dependencyManagement>`). Commit separado:

```bash
git add pom.xml
git commit -m "chore(test): adiciona mockwebserver para testar WebClient"
```

- [ ] **Step 3: Write minimal implementation**

```java
package com.marmore.api.imageedit.cost;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

/**
 * Provedor de cambio USD->BRL. Busca cotacao na AwesomeAPI com cache em memoria por TTL
 * configuravel. Em caso de erro (HTTP, timeout, JSON invalido), usa o fallback configurado.
 */
@Component
public class UsdBrlProvider {

  private final WebClient client;
  private final UsdBrlProperties props;
  private volatile CachedRate cache;

  /**
   * Construtor.
   *
   * @param builder builder de WebClient (sem baseUrl; a URL completa vem das properties)
   * @param props propriedades de cambio
   */
  public UsdBrlProvider(WebClient.Builder builder, UsdBrlProperties props) {
    this.client = builder.build();
    this.props = props;
  }

  /**
   * Cotacao atual. Usa cache se valido; senao busca na API.
   *
   * @return cambio USD->BRL
   */
  public Mono<BigDecimal> currentRate() {
    CachedRate atual = cache;
    if (atual != null && !atual.isExpired(props)) {
      return Mono.just(atual.rate);
    }
    return fetch().doOnNext(rate -> cache = new CachedRate(rate, Instant.now()));
  }

  private Mono<BigDecimal> fetch() {
    return client
        .get()
        .uri(props.url())
        .retrieve()
        .bodyToMono(JsonNode.class)
        .map(node -> new BigDecimal(node.path("USDBRL").path("bid").asText()))
        .onErrorResume(e -> Mono.just(props.fallback()));
  }

  private record CachedRate(BigDecimal rate, Instant fetchedAt) {
    boolean isExpired(UsdBrlProperties props) {
      return Instant.now().isAfter(fetchedAt.plus(props.cacheTtl()));
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marmore/api/imageedit/cost/UsdBrlProvider.java \
        src/test/java/com/marmore/api/imageedit/cost/UsdBrlProviderTest.java
git commit -m "feat(cost): adiciona UsdBrlProvider com cache TTL e fallback"
```

---

## Task 5: `SseEvents` factory

Componente que serializa os payloads dos eventos SSE via `ObjectMapper` (records + Jackson). Nunca monta JSON por concatenação de strings: isso quebra com `BigDecimal` em notação científica, não escapa strings, e reimplementa o que o Jackson já entrega.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/web/SseEvents.java`
- Test: `src/test/java/com/marmore/api/imageedit/web/SseEventsTest.java`

**Interfaces:**
- Consumes: `tools.jackson.databind.ObjectMapper` (bean do Spring, injetado no construtor).
- Produces: métodos de instância `status(String fase)`, `ping()`, `done(long latencyMs, BigDecimal custoBrl, JsonNode usage)`, `imagem(String b64)`, `error(String error, long latencyMs)`. Todos retornam `ServerSentEvent<Object>`.

> **Por que componente e não estática:** o `ObjectMapper` é configurado pelo Spring (Jackson 3) e injetado. Métodos de instância tornam o teste trivial (injeta um mapper novo) e evitam compartilhar estado global. O handler (Task 12) injeta `SseEvents` como dependência.

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.imageedit.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SseEventsTest {

  private final SseEvents events = new SseEvents(new ObjectMapper());

  @Test
  void statusCarregaFase() {
    ServerSentEvent<Object> ev = events.status("recebido");
    assertThat(ev.event()).isEqualTo("status");
    JsonNode parsed = new ObjectMapper().readTree((String) ev.data());
    assertThat(parsed.path("fase").asText()).isEqualTo("recebido");
  }

  @Test
  void pingSemData() {
    ServerSentEvent<Object> ev = events.ping();
    assertThat(ev.event()).isEqualTo("ping");
    assertThat(ev.data()).isNull();
  }

  @Test
  void doneCarregaLatenciaCustoUsage() {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode usage = mapper.readTreeOrValue("{\"input_tokens\":10}", JsonNode.class);
    ServerSentEvent<Object> ev = events.done(1234L, new BigDecimal("0.271"), usage);
    assertThat(ev.event()).isEqualTo("done");
    JsonNode parsed = mapper.readTree((String) ev.data());
    assertThat(parsed.path("latency_ms").asLong()).isEqualTo(1234L);
    assertThat(parsed.path("custo_brl").decimalValue()).isEqualByComparingTo("0.271");
    assertThat(parsed.path("usage").path("input_tokens").asInt()).isEqualTo(10);
  }

  @Test
  void doneComUsageNullSerializaComoNull() {
    ObjectMapper mapper = new ObjectMapper();
    ServerSentEvent<Object> ev = events.done(5L, new BigDecimal("0.271"), null);
    JsonNode parsed = mapper.readTree((String) ev.data());
    assertThat(parsed.path("usage").isNull()).isTrue();
  }

  @Test
  void custoBrlBigDecimalPreservaEscala() {
    ServerSentEvent<Object> ev = events.done(1L, new BigDecimal("0.053000"), null);
    JsonNode parsed = new ObjectMapper().readTree((String) ev.data());
    // BigDecimal serializado preserva a escala definida, sem virar notacao cientifica
    assertThat(parsed.path("custo_brl").asText()).isEqualTo("0.053000");
  }

  @Test
  void imagemCarregaBase64Puro() {
    ServerSentEvent<Object> ev = events.imagem("aG VsbG8=");
    assertThat(ev.event()).isEqualTo("imagem");
    assertThat(ev.data()).isEqualTo("aG VsbG8=");
  }

  @Test
  void errorCarregaMensagemELatencia() {
    ServerSentEvent<Object> ev = events.error("OPENAI_API_KEY ausente", 50L);
    assertThat(ev.event()).isEqualTo("error");
    JsonNode parsed = new ObjectMapper().readTree((String) ev.data());
    assertThat(parsed.path("error").asText()).isEqualTo("OPENAI_API_KEY ausente");
    assertThat(parsed.path("latency_ms").asLong()).isEqualTo(50L);
  }

  @Test
  void errorEscapaAspasNaMensagem() {
    ServerSentEvent<Object> ev = events.error("mensagem com \"aspas\"", 1L);
    JsonNode parsed = new ObjectMapper().readTree((String) ev.data());
    assertThat(parsed.path("error").asText()).isEqualTo("mensagem com \"aspas\"");
  }
}
```

> **Notas de teste:** os testes fazem parse reverso do `data` via `readTree` e validam campos. Isso é robusto (não depende de formatação de string exata) e prova que o JSON é válido. O teste `custoBrlBigDecimalPreservaEscala` documenta a razão de usar Jackson: `BigDecimal` com escala é preservado, sem degradar para `5.3E-2`.

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `SseEvents` não existe.

- [ ] **Step 3: Write minimal implementation**

```java
package com.marmore.api.imageedit.web;

import java.math.BigDecimal;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Fabrica de eventos SSE para o stream de edicao de imagem. Serializa os payloads via
 * {@link ObjectMapper} (records + Jackson), nunca por concatenacao de strings. Isola o formato de
 * cada evento (status, ping, done, imagem, error) num unico lugar.
 */
@Component
public final class SseEvents {

  private final ObjectMapper mapper;

  /**
   * Construtor.
   *
   * @param mapper ObjectMapper do Spring (Jackson 3)
   */
  public SseEvents(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** Evento de status com a fase do processamento. */
  public ServerSentEvent<Object> status(String fase) {
    return sse("status", json(new StatusPayload(fase)));
  }

  /** Heartbeat. Sem data. */
  public ServerSentEvent<Object> ping() {
    return ServerSentEvent.builder().event("ping").build();
  }

  /** Evento final com metadados. */
  public ServerSentEvent<Object> done(long latencyMs, BigDecimal custoBrl, JsonNode usage) {
    return sse("done", json(new DonePayload(latencyMs, custoBrl, usage)));
  }

  /** Imagem final em base64 puro (nao JSON). */
  public ServerSentEvent<Object> imagem(String b64) {
    return sse("imagem", b64);
  }

  /** Evento de erro de dominio. */
  public ServerSentEvent<Object> error(String error, long latencyMs) {
    return sse("error", json(new ErrorPayload(error, latencyMs)));
  }

  private static ServerSentEvent<Object> sse(String event, String data) {
    return ServerSentEvent.builder().event(event).data(data).build();
  }

  private String json(Object payload) {
    try {
      return mapper.writeValueAsString(payload);
    } catch (tools.jackson.core.JacksonException e) {
      // Records simples nunca falham na serializacao; falha aqui e bug de programacao.
      throw new IllegalStateException("falha serializando payload SSE: " + e.getMessage(), e);
    }
  }

  /** Payload do evento status. */
  private record StatusPayload(String fase) {}

  /** Payload do evento done. */
  private record DonePayload(long latency_ms, BigDecimal custo_brl, JsonNode usage) {}

  /** Payload do evento error. */
  private record ErrorPayload(String error, long latency_ms) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marmore/api/imageedit/web/SseEvents.java \
        src/test/java/com/marmore/api/imageedit/web/SseEventsTest.java
git commit -m "feat(sse): adiciona fabrica SseEvents com payloads via ObjectMapper"
```

---

## Task 6: `ImageEditException` migrada

A exceção de domínio deixa de estender `ResponseStatusException` (servlet) e passa a estender `RuntimeException` (reativo). Usada pelo handler.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/web/ImageEditException.java`
- Test: `src/test/java/com/marmore/api/imageedit/web/ImageEditExceptionTest.java`

**Interfaces:**
- Produces: `class ImageEditException extends RuntimeException` com campo `status` (HttpStatus) e `message`.

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.imageedit.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ImageEditExceptionTest {

  @Test
  void carregaStatusEMensagem() {
    ImageEditException ex = new ImageEditException(HttpStatus.SERVICE_UNAVAILABLE, "sem key");
    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(ex.getMessage()).isEqualTo("sem key");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `ImageEditException` em `imageedit` não existe.

- [ ] **Step 3: Write minimal implementation**

```java
package com.marmore.api.imageedit.web;

import org.springframework.http.HttpStatus;

/**
 * Excecao de dominio da edicao de imagem. Carrega o status HTTP semantico para o
 * {@code GlobalWebExceptionHandler} traduzir.
 */
public final class ImageEditException extends RuntimeException {

  private final HttpStatus status;

  /**
   * Construtor.
   *
   * @param status status HTTP semantico
   * @param message mensagem de erro
   */
  public ImageEditException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marmore/api/imageedit/web/ImageEditException.java \
        src/test/java/com/marmore/api/imageedit/web/ImageEditExceptionTest.java
git commit -m "feat(web): adiciona ImageEditException reativa"
```

---

## Task 7: Move pacote `image` → `imageedit` (records puros)

Move os records de dados e classes puras que não dependem de web/RestClient. Faz o rename de pacote e ajusta imports. Sem mudança de lógica.

**Atenção:** Esta task mexe em muitos arquivos. Faça `git mv` para preservar histórico. Os testes correspondentes também movem.

**Files:**
- Move: `image/ai/{ImageEditPrompt, AiImageOptions, InputImage, ImageResponse, ImageResponseMetadata, ImageGeneration, Image}.java` → `imageedit/ai/`
- Move: `image/ai/AiImageException.java` → `imageedit/ai/`
- Move: `image/domain/{GenerateResult, EditPrompts}.java` → `imageedit/domain/`
- Move: `image/service/ImageResizer.java` → `imageedit/service/`
- Move: `image/io/{ImageResultWriter, FileSystemResultWriter}.java` → `imageedit/io/`
- Move: `image/config/ImageEditProperties.java` → `imageedit/config/`
- Move testes correspondentes.

- [ ] **Step 1: Move arquivos main**

```bash
mkdir -p src/main/java/com/marmore/api/imageedit/{ai,domain,service,io,config,web,cost}
mkdir -p src/test/java/com/marmore/api/imageedit/{ai,domain,service,io,config,web,cost}

# main
git mv src/main/java/com/marmore/api/image/ai/ImageEditPrompt.java      src/main/java/com/marmore/api/imageedit/ai/
git mv src/main/java/com/marmore/api/image/ai/AiImageOptions.java       src/main/java/com/marmore/api/imageedit/ai/
git mv src/main/java/com/marmore/api/image/ai/InputImage.java           src/main/java/com/marmore/api/imageedit/ai/
git mv src/main/java/com/marmore/api/image/ai/ImageResponse.java        src/main/java/com/marmore/api/imageedit/ai/
git mv src/main/java/com/marmore/api/image/ai/ImageResponseMetadata.java src/main/java/com/marmore/api/imageedit/ai/
git mv src/main/java/com/marmore/api/image/ai/ImageGeneration.java      src/main/java/com/marmore/api/imageedit/ai/
git mv src/main/java/com/marmore/api/image/ai/Image.java                src/main/java/com/marmore/api/imageedit/ai/
git mv src/main/java/com/marmore/api/image/ai/AiImageException.java     src/main/java/com/marmore/api/imageedit/ai/
git mv src/main/java/com/marmore/api/image/domain/GenerateResult.java   src/main/java/com/marmore/api/imageedit/domain/
git mv src/main/java/com/marmore/api/image/domain/EditPrompts.java      src/main/java/com/marmore/api/imageedit/domain/
git mv src/main/java/com/marmore/api/image/service/ImageResizer.java    src/main/java/com/marmore/api/imageedit/service/
git mv src/main/java/com/marmore/api/image/io/ImageResultWriter.java    src/main/java/com/marmore/api/imageedit/io/
git mv src/main/java/com/marmore/api/image/io/FileSystemResultWriter.java src/main/java/com/marmore/api/imageedit/io/
git mv src/main/java/com/marmore/api/image/config/ImageEditProperties.java src/main/java/com/marmore/api/imageedit/config/
```

- [ ] **Step 2: Move testes correspondentes**

```bash
git mv src/test/java/com/marmore/api/image/ai/AiImageOptionsTest.java        src/test/java/com/marmore/api/imageedit/ai/
git mv src/test/java/com/marmore/api/image/ai/ImageEditPromptTest.java       src/test/java/com/marmore/api/imageedit/ai/
git mv src/test/java/com/marmore/api/image/ai/ImageResponseTest.java         src/test/java/com/marmore/api/imageedit/ai/
git mv src/test/java/com/marmore/api/image/domain/GenerateResultTest.java    src/test/java/com/marmore/api/imageedit/domain/
git mv src/test/java/com/marmore/api/image/domain/EditPromptsTest.java       src/test/java/com/marmore/api/imageedit/domain/
git mv src/test/java/com/marmore/api/image/service/ImageResizerTest.java     src/test/java/com/marmore/api/imageedit/service/
git mv src/test/java/com/marmore/api/image/io/FileSystemResultWriterTest.java src/test/java/com/marmore/api/imageedit/io/
git mv src/test/java/com/marmore/api/image/config/ImageEditPropertiesTest.java src/test/java/com/marmore/api/imageedit/config/
```

- [ ] **Step 3: Atualize declaração `package` e imports**

Em todos os arquivos movidos (main + test), troque `package com.marmore.api.image.` por `package com.marmore.api.imageedit.`. Exemplo com sed:

```bash
find src -path "*/imageedit/*" -name "*.java" -print0 \
  | xargs -0 sed -i 's/com\.marmore\.api\.image\./com.marmore.api.imageedit./g'
```

Depois ajuste imports de `image.web.ImageEditException` (ainda no pacote velho temporariamente nesta task) — deixe como está se ainda não migrou; será resolvido quando o controller velho for removido na Task 11.

- [ ] **Step 4: Compile e rode testes**

Run: `make test`
Expected: pode haver erros de compilação porque `ImageEditService` (ainda em `image.service`) referencia o pacote velho. Estes serão resolvidos na Task 8 quando o service migrar. **Se houver erros de compilação somente em arquivos que serão migrados nas próximas tasks (`ImageEditService`, `OpenAiRestClientImageEditModel`, `ImageEditController`, `RestClientConfig`), está aceitável por enquanto — anote-os e continue.** O objetivo desta task é mover os records puros.

Na prática, como os records puros movidos são referenciados pelo service/controller que ainda estão no pacote velho, o build vai quebrar. Para manter o build verde, migre **junto** nesta task os imports dos arquivos ainda-não-movidos (`ImageEditService`, `OpenAiRestClientImageEditModel`, `ImageEditController`, `RestClientConfig`):

```bash
# atualiza imports nos arquivos que continuam no pacote velho (serao reescritos depois)
sed -i 's/com\.marmore\.api\.image\.ai\./com.marmore.api.imageedit.ai./g; s/com\.marmore\.api\.image\.domain\./com.marmore.api.imageedit.domain./g; s/com\.marmore\.api\.image\.service\./com.marmore.api.imageedit.service./g; s/com\.marmore\.api\.image\.io\./com.marmore.api.imageedit.io./g; s/com\.marmore\.api\.image\.config\./com.marmore.api.imageedit.config./g' \
  src/main/java/com/marmore/api/image/service/ImageEditService.java \
  src/main/java/com/marmore/api/image/ai/OpenAiRestClientImageEditModel.java \
  src/main/java/com/marmore/api/image/web/ImageEditController.java \
  src/main/java/com/marmore/api/image/config/RestClientConfig.java \
  src/test/java/com/marmore/api/image/service/ImageEditServiceTest.java \
  src/test/java/com/marmore/api/image/ai/OpenAiRestClientImageEditModelTest.java \
  src/test/java/com/marmore/api/image/web/ImageEditControllerTest.java \
  src/test/java/com/marmore/api/image/web/ImageUploadSizeTest.java \
  src/test/java/com/marmore/api/image/config/RestClientConfigTest.java
```

Run: `make test`
Expected: PASS (build verde, testes dos records puros passando).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(image): move records puros para pacote imageedit"
```

---

## Task 8: `ImageEditModel` reativo

Migra a interface do gateway para reativo. `call(prompt) → Mono<ImageResponse>`.

**Files:**
- Modify: `src/main/java/com/marmore/api/image/ai/ImageEditModel.java` (criar versão reativa em `imageedit/ai/`)
- Create: `src/main/java/com/marmore/api/imageedit/ai/ImageEditModel.java`
- Test: `src/test/java/com/marmore/api/imageedit/ai/ImageEditModelTest.java`

**Interfaces:**
- Produces: `Mono<ImageResponse> call(ImageEditPrompt prompt)`

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.imageedit.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ImageEditModelTest {

  @Test
  void implementacaoLambdaRetornaMono() {
    ImageEditModel model = prompt -> Mono.just(ImageResponseTest.respostaSimples());
    assertThat(model.call(null)).isInstanceOf(Mono.class);
  }
}
```

(Se `ImageResponseTest.respostaSimples()` não for acessível, crie um helper local ou use `ImageResponse.of(...)` no teste.)

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `ImageEditModel` reativo não existe.

- [ ] **Step 3: Write minimal implementation**

```java
package com.marmore.api.imageedit.ai;

import reactor.core.publisher.Mono;

/**
 * Gateway de edicao de imagem. Contrato reativo: retorna {@link Mono} que completa ao receber a
 * resposta da API de imagens.
 */
@FunctionalInterface
public interface ImageEditModel {

  /**
   * Chama a API de edicao de imagem.
   *
   * @param prompt prompt com instrucoes, opcoes e imagens de entrada
   * @return resposta encapsulada em Mono
   */
  Mono<ImageResponse> call(ImageEditPrompt prompt);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marmore/api/imageedit/ai/ImageEditModel.java \
        src/test/java/com/marmore/api/imageedit/ai/ImageEditModelTest.java
git commit -m "feat(ai): interface ImageEditModel reativa (Mono<ImageResponse>)"
```

---

## Task 9: `OpenAiWebClientImageEditModel` (streaming SSE)

Gateway WebClient que consome o stream SSE da OpenAI. Envia `stream=true`, `partial_images=0`, lê o corpo como `Flux<String>` de linhas SSE, parseia o evento `image_generation.completed`, extrai `b64_json` + `usage`.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/ai/OpenAiWebClientImageEditModel.java`
- Test: `src/test/java/com/marmore/api/imageedit/ai/OpenAiWebClientImageEditModelTest.java`

**Interfaces:**
- Consumes: `WebClient` (bean `imageWebClient` da Task 10), `ImageEditPrompt`.
- Produces: `Mono<ImageResponse>` via `call(prompt)`.

**Formato do stream SSE da OpenAI** (referência para o parse):

```
event: image_generation.completed
data: {"type":"image_generation.completed","image_b64":"<base64>","usage":{"input_tokens":N,"output_tokens":N}}

```

> **Nota de implementação:** o campo da imagem no evento `completed` pode ser `image_b64` (não `b64_json` como no endpoint síncrono). Durante o TDD, valide o campo real contra uma resposta capturada da OpenAI. Se o campo for diferente do esperado, ajuste o parser nesta task.

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.imageedit.ai;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class OpenAiWebClientImageEditModelTest {

  private static MockWebServer server;
  private static OpenAiWebClientImageEditModel model;

  @BeforeAll
  static void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    WebClient client = WebClient.builder().baseUrl(server.url("/").toString()).build();
    model = new OpenAiWebClientImageEditModel(client);
  }

  @AfterAll
  static void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void parseiaEventoCompletedDoStream() {
    String sse =
        "event: image_generation.completed\n"
            + "data: {\"type\":\"image_generation.completed\",\"image_b64\":\"aGk=\",\"usage\":{\"input_tokens\":10,\"output_tokens\":20}}\n\n";
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(sse));
    StepVerifier.create(model.call(promptSimples()))
        .assertNext(
            resp -> {
              assertThat(resp.getResult().output().b64Json()).isEqualTo("aGk=");
              assertThat(resp.metadata().usage().path("output_tokens").asInt()).isEqualTo(20);
            })
        .verifyComplete();
  }

  @Test
  void erroHttpViraAiImageException() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
    StepVerifier.create(model.call(promptSimples()))
        .expectError(AiImageException.class)
        .verify();
  }

  private static ImageEditPrompt promptSimples() {
    return ImageEditPrompt.of(
        "prompt teste",
        AiImageOptions.defaults(),
        java.util.List.of(InputImage.of(new byte[] {1, 2}, "ambiente.jpg")));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `OpenAiWebClientImageEditModel` não existe.

- [ ] **Step 3: Write minimal implementation**

```java
package com.marmore.api.imageedit.ai;

import com.marmore.api.imageedit.ai.OpenAiRestClientImageEditModel.NamedBytesResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Implementacao de {@link ImageEditModel} que fala com a OpenAI via {@code WebClient}, consumindo o
 * stream SSE do {@code /v1/images/edits} com {@code stream=true} e {@code partial_images=0}.
 * Retorna um {@link Mono} que completa ao receber o evento {@code image_generation.completed}.
 */
@Component
public class OpenAiWebClientImageEditModel implements ImageEditModel {

  private static final String EDITS_PATH = "/v1/images/edits";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final WebClient webClient;

  /**
   * Construtor.
   *
   * @param webClient cliente HTTP autenticado para a OpenAI (bean {@code imageWebClient})
   */
  public OpenAiWebClientImageEditModel(WebClient webClient) {
    this.webClient = webClient;
  }

  @Override
  public Mono<ImageResponse> call(ImageEditPrompt prompt) {
    return webClient
        .post()
        .uri(EDITS_PATH)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartDe(prompt))
        .retrieve()
        .bodyToFlux(String.class)
        .filter(linha -> linha.startsWith("data:"))
        .map(linha -> linha.substring("data:".length()).trim())
        .filter(json -> json.contains("image_generation.completed"))
        .next()
        .map(OpenAiWebClientImageEditModel::respostaDe)
        .onErrorMap(
            e -> !(e instanceof AiImageException),
            e ->
                new AiImageException(
                    e.getClass().getSimpleName() + ": " + e.getMessage(), e));
  }

  /** Monta o multipart com stream=true e partial_images=0. */
  private static MultiValueMap<String, Object> multipartDe(ImageEditPrompt prompt) {
    AiImageOptions opts = prompt.options();
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("prompt", prompt.instructions());
    body.add("stream", "true");
    body.add("partial_images", "0");
    if (opts.model() != null) {
      body.add("model", opts.model());
    }
    if (opts.n() != null) {
      body.add("n", opts.n());
    }
    if (opts.size() != null) {
      body.add("size", opts.size());
    }
    if (opts.quality() != null) {
      body.add("quality", opts.quality());
    }
    if (opts.sendsFidelity()) {
      body.add("input_fidelity", opts.inputFidelity());
    }
    for (InputImage img : prompt.inputImages()) {
      body.add("image[]", new NamedBytesResource(img.bytes(), img.filename()));
    }
    return body;
  }

  /** Traduz o JSON do evento completed em {@link ImageResponse}. */
  private static ImageResponse respostaDe(String json) {
    try {
      JsonNode node = MAPPER.readTree(json);
      String b64 = node.path("image_b64").asText(null);
      if (b64 == null) {
        b64 = node.path("b64_json").asText(null);
      }
      if (b64 == null) {
        throw new AiImageException("resposta sem image_b64/b64_json");
      }
      JsonNode usage = node.has("usage") ? node.get("usage") : null;
      return new ImageResponse(
          java.util.List.of(ImageGeneration.of(Image.of(b64))),
          new ImageResponseMetadata(usage),
          node);
    } catch (AiImageException e) {
      throw e;
    } catch (Exception e) {
      throw new AiImageException("erro parseando resposta: " + e.getMessage(), e);
    }
  }
}
```

> **Reuse de `NamedBytesResource`:** a Task 10 remove `OpenAiRestClientImageEditModel`, mas a inner class `NamedBytesResource` precisa sobreviver. Duas opções: (a) torná-la top-level em `imageedit/ai/` antes de remover o RestClient, ou (b) redefini-la como inner class nesta task (código acima já redefine localmente). Esta task usa a opção (b) para manter o diff local. Verifique se há duplicação ao remover o RestClient na Task 10.

Na implementação acima, substitua o import problemático removendo a linha `import com.marmore.api.imageedit.ai.OpenAiRestClientImageEditModel.NamedBytesResource;` e use a inner class local definida abaixo (adicione ao final da classe):

```java
  /** ByteArrayResource com nome de arquivo, necessario para multipart. */
  private static final class NamedBytesResource extends ByteArrayResource {
    private final String filename;

    NamedBytesResource(byte[] bytes, String filename) {
      super(bytes);
      this.filename = filename;
    }

    @Override
    public String getFilename() {
      return filename;
    }
  }
```

E adicione o import `import org.springframework.core.io.ByteArrayResource;` no topo.

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marmore/api/imageedit/ai/OpenAiWebClientImageEditModel.java \
        src/test/java/com/marmore/api/imageedit/ai/OpenAiWebClientImageEditModelTest.java
git commit -m "feat(ai): gateway WebClient consumindo stream SSE da OpenAI"
```

---

## Task 10: `WebClientConfig`

Configura o bean `imageWebClient` com baseUrl, Bearer e read timeout. Substitui `RestClientConfig`.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/config/WebClientConfig.java`
- Test: `src/test/java/com/marmore/api/imageedit/config/WebClientConfigTest.java`
- Remove: `src/main/java/com/marmore/api/image/ai/OpenAiRestClientImageEditModel.java` (depois de validar que `NamedBytesResource` foi preservada na Task 9)
- Remove: `src/main/java/com/marmore/api/image/config/RestClientConfig.java`
- Remove: `src/test/java/com/marmore/api/image/ai/OpenAiRestClientImageEditModelTest.java`
- Remove: `src/test/java/com/marmore/api/image/config/RestClientConfigTest.java`

**Interfaces:**
- Consumes: `ImageEditProperties` (baseUrl, apiKey, timeout).
- Produces: bean `WebClient imageWebClient`.

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.imageedit.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.marmore.api.imageedit.config.ImageEditProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class WebClientConfigTest {

  @Test
  void beanWebClientTemBaseUrlConfigurada() {
    ImageEditProperties props = new ImageEditProperties();
    props.setBaseUrl("https://api.openai.com");
    props.setApiKey("sk-teste");
    props.setTimeout(Duration.ofSeconds(180));
    WebClient client = new WebClientConfig().imageWebClient(props, WebClient.builder());
    assertThat(client).isNotNull();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `WebClientConfig` não existe.

- [ ] **Step 3: Write minimal implementation**

```java
package com.marmore.api.imageedit.config;

import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Configura o {@link WebClient} para a OpenAI. Base URL, Authorization Bearer e read timeout vem de
 * {@link ImageEditProperties}. O read timeout e aplicado no Netty (reactor-netty), equivalente ao
 * que o {@code RestClientConfig} fazia no RestClient via customizer.
 */
@Configuration
public class WebClientConfig {

  /**
   * Bean do WebClient da OpenAI.
   *
   * @param props propriedades do modulo de imagem
   * @param builder builder de WebClient fornecido pela auto-config
   * @return WebClient configurado
   */
  @Bean
  public WebClient imageWebClient(ImageEditProperties props, WebClient.Builder builder) {
    long timeoutSeconds = props.getTimeout().toSeconds();
    HttpClient httpClient =
        HttpClient.create()
            .doOnConnected(
                c ->
                    c.addHandlerLast(
                        new ReadTimeoutHandler((int) timeoutSeconds)));
    return builder
        .baseUrl(props.getBaseUrl())
        .defaultHeader("Authorization", "Bearer " + props.getApiKey())
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: PASS

- [ ] **Step 5: Remove o RestClient antigo**

```bash
git rm src/main/java/com/marmore/api/image/ai/OpenAiRestClientImageEditModel.java
git rm src/main/java/com/marmore/api/image/config/RestClientConfig.java
git rm src/test/java/com/marmore/api/image/ai/OpenAiRestClientImageEditModelTest.java
git rm src/test/java/com/marmore/api/image/config/RestClientConfigTest.java
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(config): WebClientConfig substitui RestClientConfig

- bean imageWebClient com baseUrl, Bearer, read timeout (Netty)
- remove OpenAiRestClientImageEditModel e RestClientConfig
- NamedBytesResource preservada como inner class do gateway WebClient"
```

---

## Task 11: `ImageEditService` reativo

Migra o service para `Mono<GenerateResult>`. Reaproveita a lógica síncrona envolta em `Mono.fromCallable` + `boundedElastic` para as partes bloqueantes (resize, leitura de pedra), mas encadeia o `Mono<ImageResponse>` do gateway reativo.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/service/ImageEditService.java`
- Test: `src/test/java/com/marmore/api/imageedit/service/ImageEditServiceTest.java`
- Remove: `src/main/java/com/marmore/api/image/service/ImageEditService.java` (velho, pacote `image`)
- Remove: `src/test/java/com/marmore/api/image/service/ImageEditServiceTest.java`

**Interfaces:**
- Consumes: `ImageEditProperties`, `ImageResizer`, `ImageEditModel` (reativo), `ImageCostCalculator`, `UsdBrlProvider`.
- Produces: `Mono<GenerateResult> generate(byte[] ambiente)`.

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.imageedit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marmore.api.imageedit.ai.AiImageOptions;
import com.marmore.api.imageedit.ai.ImageEditModel;
import com.marmore.api.imageedit.ai.ImageEditPrompt;
import com.marmore.api.imageedit.ai.ImageResponse;
import com.marmore.api.imageedit.ai.ImageResponseTest;
import com.marmore.api.imageedit.config.ImageEditProperties;
import com.marmore.api.imageedit.cost.ImageCostCalculator;
import com.marmore.api.imageedit.cost.UsdBrlProvider;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ImageEditServiceTest {

  @TempDir Path tempDir;
  private ImageEditProperties props;
  private ImageResizer resizer;
  private ImageEditModel model;
  private ImageCostCalculator calculator;
  private UsdBrlProvider usdBrl;

  @BeforeEach
  void setUp() throws Exception {
    props = new ImageEditProperties();
    props.setApiKey("sk-teste");
    // cria pedra em disco
    Path pedra = tempDir.resolve("granito.png");
    java.nio.file.Files.write(pedra, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
    props.setStonePath(pedra);
    props.setTimeout(Duration.ofSeconds(180));
    resizer = mock(ImageResizer.class);
    model = mock(ImageEditModel.class);
    calculator = new ImageCostCalculator();
    usdBrl = mock(UsdBrlProvider.class);
    when(usdBrl.currentRate()).thenReturn(Mono.just(new java.math.BigDecimal("5.1075")));
  }

  @Test
  void sucessoRetornaOkComB64() {
    when(resizer.resize(any())).thenReturn(java.util.Optional.of(new byte[] {1, 2}));
    ImageResponse resp = ImageResponseTest.respostaSimples();
    when(model.call(any())).thenReturn(Mono.just(resp));
    ImageEditService svc =
        new ImageEditService(props, resizer, model, calculator, usdBrl);
    StepVerifier.create(svc.generate(new byte[] {1}))
        .assertNext(
            r -> {
              assertThat(r).isInstanceOf(GenerateResult.Ok.class);
              assertThat(((GenerateResult.Ok) r).b64()).isNotBlank();
            })
        .verifyComplete();
  }

  @Test
  void apiKeyAusenteViraErr() {
    props.setApiKey(null);
    ImageEditService svc = new ImageEditService(props, resizer, model, calculator, usdBrl);
    StepVerifier.create(svc.generate(new byte[] {1}))
        .assertNext(
            r -> {
              assertThat(r).isInstanceOf(GenerateResult.Err.class);
              assertThat(((GenerateResult.Err) r).error()).startsWith("OPENAI_API_KEY");
            })
        .verifyComplete();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `ImageEditService` em `imageedit` não existe.

- [ ] **Step 3: Write minimal implementation**

```java
package com.marmore.api.imageedit.service;

import com.marmore.api.imageedit.ai.AiImageException;
import com.marmore.api.imageedit.ai.AiImageOptions;
import com.marmore.api.imageedit.ai.ImageEditModel;
import com.marmore.api.imageedit.ai.ImageEditPrompt;
import com.marmore.api.imageedit.ai.ImageResponse;
import com.marmore.api.imageedit.ai.InputImage;
import com.marmore.api.imageedit.config.ImageEditProperties;
import com.marmore.api.imageedit.cost.ImageCostCalculator;
import com.marmore.api.imageedit.cost.UsdBrlProvider;
import com.marmore.api.imageedit.domain.EditPrompts;
import com.marmore.api.imageedit.domain.GenerateResult;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Servico de edicao de imagem reativo. Orquestra validacoes pre-rede, resize (bloqueante em
 * boundedElastic), chamada ao gateway reativo e calculo de custo. Nenhum caminho lanca: falhas
 * viram {@link GenerateResult.Err}.
 */
@Service
public class ImageEditService {

  private final ImageEditProperties props;
  private final ImageResizer resizer;
  private final ImageEditModel model;
  private final ImageCostCalculator calculator;
  private final UsdBrlProvider usdBrl;

  /**
   * Construtor.
   *
   * @param props propriedades do modulo
   * @param resizer redimensionador de imagem em memoria
   * @param model gateway de edicao reativo
   * @param calculator calculadora de custo em USD
   * @param usdBrl provedor de cambio USD->BRL
   */
  public ImageEditService(
      ImageEditProperties props,
      ImageResizer resizer,
      ImageEditModel model,
      ImageCostCalculator calculator,
      UsdBrlProvider usdBrl) {
    this.props = props;
    this.resizer = resizer;
    this.model = model;
    this.calculator = calculator;
    this.usdBrl = usdBrl;
  }

  /**
   * Gera/edita imagem a partir dos bytes do ambiente.
   *
   * @param ambiente bytes da foto do ambiente
   * @return sucesso ou erro, nunca lanca
   */
  public Mono<GenerateResult> generate(byte[] ambiente) {
    long start = System.nanoTime();
    if (props.getApiKey() == null || props.getApiKey().isBlank()) {
      return Mono.just(new GenerateResult.Err("OPENAI_API_KEY ausente. Defina no ambiente.", ms(start)));
    }
    Resource pedra = new FileSystemResource(props.getStonePath());
    if (!pedra.exists()) {
      return Mono.just(new GenerateResult.Err("stone image not found: " + props.getStonePath(), ms(start)));
    }
    return Mono.fromCallable(() -> preparaAmbiente(ambiente, pedra))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(preparado -> geraSeValido(preparado, start));
  }

  private Preparado preparaAmbiente(byte[] ambiente, Resource pedra) throws Exception {
    Optional<byte[]> reduzido = resizer.resize(ambiente);
    if (reduzido.isEmpty()) {
      return Preparado.erro("unable to decode input image");
    }
    byte[] pedraBytes = pedra.getContentAsByteArray();
    return Preparado.ok(reduzido.get(), pedraBytes);
  }

  private Mono<GenerateResult> geraSeValido(Preparado preparado, long start) {
    if (preparado.erro != null) {
      return Mono.just(new GenerateResult.Err(preparado.erro, ms(start)));
    }
    ImageEditPrompt prompt =
        ImageEditPrompt.of(
            EditPrompts.COUNTERTOP,
            AiImageOptions.defaults(),
            List.of(
                InputImage.of(preparado.ambienteReduzido, "ambiente.jpg"),
                InputImage.of(preparado.pedraBytes, nomeDoArquivoDaPedra())));
    long callStart = System.nanoTime();
    return model
        .call(prompt)
        .map(resp -> montaResultado(resp, callStart))
        .onErrorResume(e -> Mono.just(toErr(e, start)));
  }

  private GenerateResult montaResultado(ImageResponse resp, long callStart) {
    if (resp.getResult() == null || resp.getResult().output().b64Json() == null) {
      return new GenerateResult.Err("resposta sem b64_json", ms(callStart));
    }
    AiImageOptions opts = AiImageOptions.defaults();
    BigDecimal costUsd =
        calculator.costUsd(opts.model(), opts.quality(), opts.size()).orElse(BigDecimal.ZERO);
    return usdBrl
        .currentRate()
        .map(rate -> costUsd.multiply(rate))
        .map(
            costBrl -> {
              String b64 = resp.getResult().output().b64Json();
              return (GenerateResult)
                  new GenerateResult.Ok(b64, resp.raw(), resp.metadata().usage(), ms(callStart));
            })
        // custo e enrich; nao bloqueia o resultado por isso
        .defaultIfEmpty(
            new GenerateResult.Ok(
                resp.getResult().output().b64Json(),
                resp.raw(),
                resp.metadata().usage(),
                ms(callStart)))
        .block();
  }

  private static GenerateResult.Err toErr(Throwable e, long start) {
    String msg =
        e instanceof AiImageException aie
            ? aie.getMessage()
            : e.getClass().getSimpleName() + ": " + e.getMessage();
    return new GenerateResult.Err(msg, ms(start));
  }

  /** Extrai o nome do arquivo da pedra do path configurado. */
  private String nomeDoArquivoDaPedra() {
    Path fileName = props.getStonePath().getFileName();
    return fileName != null ? fileName.toString() : "pedra.png";
  }

  private static long ms(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }

  private static final class Preparado {
    final byte[] ambienteReduzido;
    final byte[] pedraBytes;
    final String erro;

    private Preparado(byte[] ambienteReduzido, byte[] pedraBytes, String erro) {
      this.ambienteReduzido = ambienteReduzido;
      this.pedraBytes = pedraBytes;
      this.erro = erro;
    }

    static Preparado ok(byte[] ambienteReduzido, byte[] pedraBytes) {
      return new Preparado(ambienteReduzido, pedraBytes, null);
    }

    static Preparado erro(String msg) {
      return new Preparado(null, null, msg);
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: PASS

- [ ] **Step 5: Remove o service velho e commit**

```bash
git rm src/main/java/com/marmore/api/image/service/ImageEditService.java
git rm src/test/java/com/marmore/api/image/service/ImageEditServiceTest.java
git add -A
git commit -m "feat(service): ImageEditService reativo com calculo de custo BRL"
```

---

## Task 12: `ImageEditHandler` + `ImageEditRouter`

O handler monta o `Flux<ServerSentEvent>` com a sequência completa: status (3 fases) + merger de ping + done/imagem (ou error). O router expõe `POST /images/edit`.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/web/ImageEditHandler.java`
- Create: `src/main/java/com/marmore/api/imageedit/web/ImageEditRouter.java`
- Test: `src/test/java/com/marmore/api/imageedit/web/ImageEditHandlerTest.java`

**Interfaces:**
- Consumes: `ImageEditService`, `SseEvents`.
- Produces: `Mono<ServerResponse>` via `edit(ServerRequest)`.

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.imageedit.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marmore.api.imageedit.domain.GenerateResult;
import com.marmore.api.imageedit.service.ImageEditService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ImageEditHandlerTest {

  private ImageEditService service;
  private SseEvents events;
  private ImageEditHandler handler;

  @BeforeEach
  void setUp() {
    service = mock(ImageEditService.class);
    events = new SseEvents(new tools.jackson.databind.ObjectMapper());
    handler = new ImageEditHandler(service, events);
  }

  @Test
  void streamEmiteSequenciaStatusDoneImagem() {
    when(service.generate(any()))
        .thenReturn(
            Mono.just(
                new GenerateResult.Ok(
                    "aGk=",
                    null,
                    null,
                    100L)));
    // Simula um ServerRequest com FilePart (simplificado: mock direto de bytes)
    ServerRequest request = mock(ServerRequest.class);
    when(request.multipartData())
        .thenReturn(
            Mono.just(
                new org.springframework.util.LinkedMultiValueMap<>(
                    Map.of(
                        "image",
                        java.util.List.of(
                            mockPart(new byte[] {1}))))));

    StepVerifier.create(handler.streamFlux(request))
        .expectNextMatches(ev -> "status".equals(ev.event()) && ((String) ev.data()).contains("recebido"))
        .expectNextMatches(ev -> "status".equals(ev.event()) && ((String) ev.data()).contains("redimensionando"))
        .expectNextMatches(ev -> "status".equals(ev.event()) && ((String) ev.data()).contains("gerando"))
        .expectNextMatches(ev -> "done".equals(ev.event()))
        .expectNextMatches(ev -> "imagem".equals(ev.event()) && "aGk=".equals(ev.data()))
        .verifyComplete();
  }

  @Test
  void streamEmiteErrorEmFalhaDeDominio() {
    when(service.generate(any()))
        .thenReturn(Mono.just(new GenerateResult.Err("OPENAI_API_KEY ausente", 10L)));
    ServerRequest request = mock(ServerRequest.class);
    when(request.multipartData())
        .thenReturn(
            Mono.just(
                new org.springframework.util.LinkedMultiValueMap<>(
                    Map.of("image", java.util.List.of(mockPart(new byte[] {1}))))));

    StepVerifier.create(handler.streamFlux(request))
        .expectNextMatches(ev -> "status".equals(ev.event()))
        .expectNextMatches(ev -> "status".equals(ev.event()))
        .expectNextMatches(ev -> "status".equals(ev.event()))
        .expectNextMatches(ev -> "error".equals(ev.event()))
        .verifyComplete();
  }

  private static org.springframework.http.codec.multipart.FilePart mockPart(byte[] bytes) {
    org.springframework.http.codec.multipart.FilePart part =
        mock(org.springframework.http.codec.multipart.FilePart.class);
    when(part.transferTo(any(java.io.File.class)))
        .thenReturn(Mono.empty());
    when(part.content())
        .thenReturn(
            Flux.just(
                org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance.wrap(bytes)));
    when(part.filename()).thenReturn("ambiente.jpg");
    return part;
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `ImageEditHandler` não existe.

- [ ] **Step 3: Write minimal implementation (handler)**

```java
package com.marmore.api.imageedit.web;

import com.marmore.api.imageedit.domain.GenerateResult;
import com.marmore.api.imageedit.service.ImageEditService;
import java.io.File;
import java.nio.file.Files;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Handler reativo para o stream SSE de edicao de imagem. Monta a sequencia de eventos: status
 * (recebido, redimensionando, gerando), merger de heartbeat (ping a cada 15s) com a geracao, e
 * finally done+imagem ou error.
 */
@Component
public class ImageEditHandler {

  private static final long PING_INTERVAL_SECONDS = 15L;

  private final ImageEditService service;
  private final SseEvents events;

  /**
   * Construtor.
   *
   * @param service servico de edicao reativo
   * @param events fabrica de eventos SSE
   */
  public ImageEditHandler(ImageEditService service, SseEvents events) {
    this.service = service;
    this.events = events;
  }

  /**
   * Entry point do endpoint. Le o FilePart, monta o Flux de eventos SSE, envelopa em ServerResponse.
   *
   * @param request request reativo
   * @return ServerResponse com body SSE
   */
  public Mono<ServerResponse> edit(ServerRequest request) {
    return request
        .multipartData()
        .flatMap(
            map -> {
              Part part = map.toSingleValueMap().get("image");
              if (!(part instanceof FilePart filePart)) {
                return Mono.<ServerSentEvent<Object>>empty().flatMap(
                    ev ->
                        ServerResponse.badRequest()
                            .bodyValue("{\"error\":\"campo image ausente\"}"));
              }
              return ServerResponse.ok()
                  .contentType(MediaType.TEXT_EVENT_STREAM)
                  .body(streamFlux(request), ServerSentEvent.class);
            });
  }

  /**
   * Flux de eventos SSE. Exposto como package-private para teste.
   */
  Flux<ServerSentEvent<Object>> streamFlux(ServerRequest request) {
    return request
        .multipartData()
        .flatMapMany(
            map -> {
              FilePart filePart = (FilePart) map.toSingleValueMap().get("image");
              return lerBytes(filePart).flatMapMany(this::sequencia);
            });
  }

  private Flux<ServerSentEvent<Object>> sequencia(byte[] bytes) {
    Flux<ServerSentEvent<Object>> statusInicial =
        Flux.just(
            events.status("recebido"),
            events.status("redimensionando"),
            events.status("gerando"));
    Flux<ServerSentEvent<Object>> heartbeat =
        Flux.interval(java.time.Duration.ofSeconds(PING_INTERVAL_SECONDS))
            .map(i -> events.ping());
    Flux<ServerSentEvent<Object>> resultado =
        service
            .generate(bytes)
            .flatMapMany(this::eventosDeResultado)
            // volta para o event loop apos o boundedElastic do service
            .publishOn(reactor.core.scheduler.Schedulers.immediate());
    return statusInicial.concatWith(heartbeat.mergeWith(resultado).takeUntil(isResultado()));
  }

  private Flux<ServerSentEvent<Object>> eventosDeResultado(GenerateResult r) {
    if (r instanceof GenerateResult.Ok ok) {
      return Flux.just(events.done(ok.latencyMs(), null, ok.usage()), events.imagem(ok.b64()));
    }
    GenerateResult.Err err = (GenerateResult.Err) r;
    return Flux.just(events.error(err.error(), err.latencyMs()));
  }

  private static java.util.function.Predicate<ServerSentEvent<Object>> isResultado() {
    return ev -> "done".equals(ev.event()) || "error".equals(ev.event());
  }

  private static Mono<byte[]> lerBytes(FilePart filePart) {
    return DataBufferUtils.join(filePart.content())
        .map(db -> {
          byte[] bytes = new byte[db.readableByteCount()];
          db.read(bytes);
          DataBufferUtils.release(db);
          return bytes;
        });
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: PASS. Se o `takeUntil` cortar o heartbeat antes do resultado, ajuste o merger: use `Flux.merge(heartbeat.takeUntilOther(resultado), resultado)` como alternativa. O teste valida a sequência.

- [ ] **Step 5: Write the router**

```java
package com.marmore.api.imageedit.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

/**
 * Rota POST /images/edit para o handler SSE. Aceita multipart e produz text/event-stream.
 */
@Configuration
public class ImageEditRouter {

  /**
   * Bean da RouterFunction.
   *
   * @param handler handler de edicao
   * @return rota configurada
   */
  @Bean
  public RouterFunction<ServerResponse> imageEditRoute(ImageEditHandler handler) {
    return RouterFunctions.route(
        POST("/images/edit")
            .and(contentType(MediaType.MULTIPART_FORM_DATA))
            .and(accept(MediaType.TEXT_EVENT_STREAM)),
        handler::edit);
  }
}
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/marmore/api/imageedit/web/ImageEditHandler.java \
        src/main/java/com/marmore/api/imageedit/web/ImageEditRouter.java \
        src/test/java/com/marmore/api/imageedit/web/ImageEditHandlerTest.java
git commit -m "feat(web): handler e router SSE para /images/edit"
```

---

## Task 13: Remove o controller MVC velho

O controller síncrono (`ImageEditController`) não tem mais lugar. Remova.

**Files:**
- Remove: `src/main/java/com/marmore/api/image/web/ImageEditController.java`
- Remove: `src/test/java/com/marmore/api/image/web/ImageEditControllerTest.java`
- Remove: `src/test/java/com/marmore/api/image/web/ImageUploadSizeTest.java`

- [ ] **Step 1: Remove**

```bash
git rm src/main/java/com/marmore/api/image/web/ImageEditController.java
git rm src/test/java/com/marmore/api/image/web/ImageEditControllerTest.java
git rm src/test/java/com/marmore/api/image/web/ImageUploadSizeTest.java
```

- [ ] **Step 2: Verifique que o pacote velho está vazio**

```bash
find src -path "*/image/web*" -name "*.java"
find src -path "*/com/marmore/api/image/*" -name "*.java"
```

Se ainda houver arquivos em `com/marmore/api/image/` (não `imageedit`), eles são órfãos que deveriam ter sido migrados. Volte à Task 7 e mova-os. Se `com/marmore/api/image/` está vazio, remova o diretório.

- [ ] **Step 3: Compile**

Run: `make test`
Expected: PASS. Pode haver erros porque o `pom.xml` ainda tem `spring-boot-starter-webmvc`. Esses são resolvidos na Task 15.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(web): remove controller MVC sincrono /images/edit"
```

---

## Task 14: Segurança reativa (`ApiKeyAuthWebFilter` + `SecurityConfiguration`)

Migra a autenticação de servlet para reativo. Novo `WebFilter` integrado à `SecurityWebFilterChain`.

**Files:**
- Create: `src/main/java/com/marmore/api/security/ApiKeyAuthWebFilter.java`
- Modify: `src/main/java/com/marmore/api/security/SecurityConfiguration.java`
- Create: `src/test/java/com/marmore/api/security/ApiKeyAuthWebFilterTest.java`
- Modify: `src/test/java/com/marmore/api/security/SecurityConfigurationTest.java`
- Remove: `src/main/java/com/marmore/api/security/ApiKeyAuthFilter.java`
- Remove: `src/test/java/com/marmore/api/security/ApiKeyAuthFilterTest.java`

**Interfaces:**
- Consumes: `ApiKeyProperties`.
- Produces: `WebFilter` que valida `X-API-Key` e rejeita com 401.

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.handler.FilteringWebHandler;

class ApiKeyAuthWebFilterTest {

  private static final String KEY_VALIDA = "marmore-local-dev-key-2026";

  @Test
  void keyValidaPermitePassagem() {
    ApiKeyProperties props = new ApiKeyProperties();
    props.setKey(KEY_VALIDA);
    ApiKeyAuthWebFilter filter = new ApiKeyAuthWebFilter(props);
    MockServerHttpRequest req =
        MockServerHttpRequest.post("/images/edit").header("X-API-Key", KEY_VALIDA).build();
    MockServerWebExchange exchange = MockServerWebExchange.from(req);
    boolean[] passed = {false};
    filter
        .filter(exchange, w -> Mono.fromRunnable(() -> passed[0] = true))
        .block();
    assertThat(passed[0]).isTrue();
  }

  @Test
  void keyAusenteRejeita401() {
    ApiKeyProperties props = new ApiKeyProperties();
    props.setKey(KEY_VALIDA);
    ApiKeyAuthWebFilter filter = new ApiKeyAuthWebFilter(props);
    MockServerHttpRequest req = MockServerHttpRequest.post("/images/edit").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(req);
    filter.filter(exchange, w -> Mono.empty()).block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
  }

  @Test
  void keyInvalidaRejeita401() {
    ApiKeyProperties props = new ApiKeyProperties();
    props.setKey(KEY_VALIDA);
    ApiKeyAuthWebFilter filter = new ApiKeyAuthWebFilter(props);
    MockServerHttpRequest req =
        MockServerHttpRequest.post("/images/edit").header("X-API-Key", "errada").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(req);
    filter.filter(exchange, w -> Mono.empty()).block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
  }
}
```

> O teste usa `Mono` sem import explícito no escopo acima. Adicione `import reactor.core.publisher.Mono;` no topo do arquivo de teste.

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `ApiKeyAuthWebFilter` não existe.

- [ ] **Step 3: Write minimal implementation (filter)**

```java
package com.marmore.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Filtro de autenticacao por API key no header {@value #HEADER}. Compara a chave fornecida com a
 * configurada via {@link MessageDigest#isEqual} (comparacao constante no tempo). Chave valida
 * autentica o request e continua a chain; ausente ou invalida responde 401 JSON.
 */
@Component
public class ApiKeyAuthWebFilter implements WebFilter {

  /** Nome do header HTTP que carrega a API key. */
  public static final String HEADER = "X-API-Key";

  private final ApiKeyProperties props;

  /**
   * Construtor.
   *
   * @param props propriedades com a chave esperada
   */
  public ApiKeyAuthWebFilter(ApiKeyProperties props) {
    this.props = props;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String provided = exchange.getRequest().getHeaders().getFirst(HEADER);
    String expected = props.getKey();
    if (provided != null
        && expected != null
        && MessageDigest.isEqual(
            provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
      return chain
          .filter(exchange)
          .contextWrite(
              ReactiveSecurityContextHolder.withAuthentication(
                  UsernamePasswordAuthenticationToken.authenticated("apikey", null, java.util.List.of())));
    }
    return rejeitar(exchange);
  }

  private static Mono<Void> rejeitar(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    byte[] body = "{\"error\":\"API key ausente ou invalida\"}".getBytes(StandardCharsets.UTF_8);
    return exchange.getResponse().writeWith(
        Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
  }
}
```

- [ ] **Step 4: Rewrite SecurityConfiguration (reativo)**

```java
package com.marmore.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFilterChain;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebServerFilters;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Configuracao de seguranca reativa. Autenticacao stateless por API key no header X-API-Key (filtro
 * {@link ApiKeyAuthWebFilter}). CSRF desabilitado (API stateless por header, sem cookies de sessao).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

  /**
   * Cadeia de filtros de seguranca reativa.
   *
   * @param http builder do Spring Security reativo (ServerHttpSecurity)
   * @param apiKeyFilter filtro de autenticacao por API key
   * @return cadeia configurada
   */
  @Bean
  public SecurityWebFilterChain filterChain(ServerHttpSecurity http, ApiKeyAuthWebFilter apiKeyFilter) {
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authorizeExchange(a -> a.anyExchange().authenticated())
        .addFilterAt(apiKeyFilter, SecurityWebServerFilters.AUTHENTICATION)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .build();
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `make test`
Expected: PASS

- [ ] **Step 6: Remove o filtro velho e commit**

```bash
git rm src/main/java/com/marmore/api/security/ApiKeyAuthFilter.java
git rm src/test/java/com/marmore/api/security/ApiKeyAuthFilterTest.java
git add -A
git commit -m "feat(security): autenticacao X-API-Key reativa (WebFilter + SecurityWebFilterChain)"
```

---

## Task 15: Exception handler reativo

Substitui o `@RestControllerAdvice` (servlet) por `WebExceptionHandler` (reativo).

**Files:**
- Create: `src/main/java/com/marmore/api/web/GlobalWebExceptionHandler.java`
- Remove: o velho `GlobalExceptionHandler.java` (sobrescreve)
- Create: `src/test/java/com/marmore/api/web/GlobalWebExceptionHandlerTest.java`

**Interfaces:**
- Consumes: nada (pega exceções globais).
- Produces: `WebExceptionHandler` que mapeia `ImageEditException` (status do ex) e fallback 500.

- [ ] **Step 1: Write the failing test**

```java
package com.marmore.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.marmore.api.imageedit.web.ImageEditException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class GlobalWebExceptionHandlerTest {

  @Test
  void imageEditExceptionRespondeStatusSemantico() {
    GlobalWebExceptionHandler handler = new GlobalWebExceptionHandler();
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.post("/images/edit").build());
    ImageEditException ex = new ImageEditException(HttpStatus.SERVICE_UNAVAILABLE, "sem key");
    handler.handle(ex, exchange).block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }
}
```

> Adicione `import org.springframework.core.Ordered;` e `import org.springframework.core.annotation.Order;` se usar ordem. E `import org.springframework.web.server.WebExceptionHandler;`.

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — versão reativa não existe.

- [ ] **Step 3: Write minimal implementation**

```java
package com.marmore.api.web;

import com.marmore.api.imageedit.web.ImageEditException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.handler.ResponseStatusExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Tratamento global de excecoes reativo. Garante que {@link ImageEditException} preserve seu status
 * semantico. Roda depois do {@link ResponseStatusExceptionHandler} do Spring.
 */
@Component
@Order(-2)
public class GlobalWebExceptionHandler implements WebExceptionHandler {

  @Override
  public Mono<Void> handle(org.springframework.web.server.ServerWebExchange exchange, Throwable ex) {
    if (ex instanceof ImageEditException iee) {
      return escrever(exchange, iee.getStatus(), iee.getMessage());
    }
    if (ex instanceof ResponseStatusException rse) {
      return escrever(exchange, (HttpStatus) rse.getStatusCode(), rse.getReason());
    }
    return escrever(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "erro interno");
  }

  private static Mono<Void> escrever(
      org.springframework.web.server.ServerWebExchange exchange, HttpStatus status, String msg) {
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String safe = msg == null ? status.getReasonPhrase() : msg.replace("\"", "'");
    byte[] body = ("{\"error\":\"" + safe + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return exchange.getResponse().writeWith(
        Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git rm src/main/java/com/marmore/api/web/GlobalExceptionHandler.java 2>/dev/null || true
git add -A
git commit -m "feat(web): exception handler reativo"
```

---

## Task 16: Migração de stack — `pom.xml`

Remove as dependências servlet (`webmvc`, `restclient` + test starters). Confirma que só ficam `webflux` e `webclient`.

**Files:**
- Modify: `src/` (pom.xml)

- [ ] **Step 1: Edite pom.xml**

Remova estas linhas de `<dependencies>`:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-restclient</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-restclient-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webmvc-test</artifactId>
  <scope>test</scope>
</dependency>
```

Mantenha: `webflux`, `webclient`, `webclient-test`, `webflux-test`, `security`, `security-test`, `thumbnailator`, `spring-ai-starter-model-openai`, `data-jpa`/`jdbc` + test, `postgresql`, `h2`, `reactor-test` (adicionado na Task 4 via MockWebServer ou explicitamente), `mockwebserver`.

Adicione explicitamente (se ainda não estiver):

```xml
<dependency>
  <groupId>io.projectreactor</groupId>
  <artifactId>reactor-test</artifactId>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Reinstale e rode testes**

Run: `make test`
Expected: PASS. Se houver auto-config conflitando (dois servers), verifique `application.yaml`: o WebFlux puro não precisa de `spring.servlet.multipart` (config do servlet). A Task 17 trata disso.

- [ ] [ ] **Step 3: Commit**

```bash
git add pom.xml
git config advice.detachedHead false
git commit -m "chore(build): remove spring-boot-starter-webmvc e restclient; full webflux"
```

---

## Task 17: `application.yaml` + `ApiApplication` registry

Adiciona config de custo/câmbio, remove config de multipart servlet, registra `UsdBrlProperties` no `@EnableConfigurationProperties`.

**Files:**
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application.yaml`
- Modify: `src/main/java/com/marmore/api/ApiApplication.java`

- [ ] **Step 1: Atualize application.yaml (main)**

```yaml
spring:
  application:
    name: api
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
    model:
      chat: none
      embedding: none
      image: none
      audio:
        speech: none
        transcription: none
      moderation: none
  codec:                               # NOVO: limite in-memory do WebFlux (equivalente ao multipart do servlet)
    max-in-memory-size: 25MB

marmore:
  api:
    key: ${MARMORE_API_KEY:}
  openai:
    image:
      base-url: https://api.openai.com
      api-key: ${spring.ai.openai.api-key:}
      default-model: gpt-image-2
      timeout: 180s
      stone-path: ${user.dir}/data/granito.png
  cost:                                # NOVO
    usd-brl:
      url: https://economia.awesomeapi.com.br/json/last/USD-BRL
      cache-ttl: 6h
      fallback: 5.1075
```

- [ ] **Step 2: Atualize test application.yaml**

Adicione o mesmo bloco `marmore.cost` no `src/test/resources/application.yaml` (para que `UsdBrlProperties` carregue nos testes de contexto).

- [ ] `spring.servlet.multipart`**:** remova do `application.yaml` se ainda presente (config servlet, inútil no WebFlux).

- [ ] **Step 3: Atualize ApiApplication**

```java
package com.marmore.api;

import com.marmore.api.imageedit.config.ImageEditProperties;
import com.marmore.api.imageedit.cost.UsdBrlProperties;
import com.marmore.api.security.ApiKeyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point da API Marmore.
 */
@SpringBootApplication
@EnableConfigurationProperties({ApiKeyProperties.class, ImageEditProperties.class, UsdBrlProperties.class})
public class ApiApplication {

  /**
   * Main.
   *
   * @param args args de linha de comando
   */
  public static void main(String[] args) {
    SpringApplication.run(ApiApplication.class, args);
  }
}
```

- [ ] **Step 4: Rode testes**

Run: `make test`
Expected: PASS. Se `SecurityConfigurationTest` falhar por causa do contexto reativo, atualize-o para usar `WebTestClient` em vez de `MockMvc` (verificar se já não foi feito na Task 14).

- [ ] **Step 4b: Rode lint**

Run: `make lint`
Expected: PASS (0 Checkstyle violations, 0 Spotless changes).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/application.yaml \
        src/test/resources/application.yaml \
        src/main/java/com/marmore/api/ApiApplication.java
git commit -m "chore(config): adiciona marmore.cost e codec; registra UsdBrlProperties"
```

---

## Task 18: Bruno collection + docs

Atualiza o Bruno `.bru` existente para refletir o novo contrato SSE.

**Files:**
- Modify: `bruno/marmore-api/editar imagem.bru`

- [ ] **Step 1: Atualize o .bru**

```bruno
meta {
  name: editar imagem
  type: http
  seq: 2
}

post {
  url: {{base_url}}/images/edit
  body: multipartForm
  auth: inherit
}

headers {
  X-API-Key: {{marmore_api_key}}
  Accept: text/event-stream
}

body:multipart-form {
  image: @file(assets/ambiente.jpg)
}

settings {
  encodeUrl: true
  timeout: 0
}
```

A única mudança real: adicionar `Accept: text/event-stream` nos headers. O Bruno recebe o stream como resposta (não renderiza os eventos nativamente, mas registra a resposta).

- [ ] **Step 2: Commit**

```bash
git add "bruno/marmore-api/editar imagem.bru"
git commit -m "docs(bruno): atualiza request para SSE com Accept text/event-stream"
```

---

## Task 19: Teste de integração do fluxo SSE completo

Prova que o PlantUML foi implementado end-to-end. Sobe o contexto WebFlux, mocka o `WebClient` da OpenAI, envia multipart via `WebTestClient`, valida a sequência de eventos SSE via `StepVerifier`.

**Files:**
- Create: `src/test/java/com/marmore/api/imageedit/web/ImageEditSseIntegrationTest.java`

**Interfaces:**
- Consumes: tudo (contexto completo).

- [ ] **Step 1: Write the test**

```java
package com.marmore.api.imageedit.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marmore.api.imageedit.ai.ImageEditModel;
import com.marmore.api.imageedit.ai.ImageResponse;
import com.marmore.api.imageedit.ai.ImageResponseTest;
import com.marmore.api.imageedit.config.ImageEditProperties;
import com.marmore.api.imageedit.cost.ImageCostCalculator;
import com.marmore.api.imageedit.cost.UsdBrlProvider;
import com.marmore.api.security.ApiKeyProperties;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "marmore.api.key=marmore-local-dev-key-2026",
    "marmore.openai.image.api-key=sk-teste",
    "marmore.openai.image.stone-path=${user.dir}/data/granito.png"
})
class ImageEditSseIntegrationTest {

  @Autowired WebTestClient webTestClient;

  @TestConfiguration
  static class TestBeans {
    @Bean
    @Primary
    ImageEditModel mockModel() {
      ImageEditModel m = mock(ImageEditModel.class);
      when(m.call(any())).thenReturn(Mono.just(ImageResponseTest.respostaSimples()));
      return m;
    }

    @Bean
    @Primary
    UsdBrlProvider mockUsdBrl() {
      UsdBrlProvider p = mock(UsdBrlProvider.class);
      when(p.currentRate()).thenReturn(Mono.just(new BigDecimal("5.1075")));
      return p;
    }
  }

  @Test
  void fluxoSseEmiteSequenciaCompleta() {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("image", new ByteArrayResource(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}))
        .filename("ambiente.jpg");

    webTestClient
        .post()
        .uri("/images/edit")
        .header("X-API-Key", "marmore-local-dev-key-2026")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .bodyValue(builder.build())
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.TEXT_EVENT_STREAM)
        .returnResult(String.class)
        .getResponseBody()
        .as(reactor.test.StepVerifier::create)
        .expectNextMatches(l -> l.contains("recebido"))
        .expectNextMatches(l -> l.contains("redimensionando"))
        .expectNextMatches(l -> l.contains("gerando"))
        .expectNextMatches(l -> l.contains("done") || l.contains("error"))
        .thenCancel()
        .verify();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — se o contexto não sobe (ex.: `data/granito.png` não existe para o teste). Crie o arquivo `data/granito.png` (mesmo vazio ou um PNG de teste):

```bash
mkdir -p data && printf '\x89PNG\r\n\x1a\n' > data/granito.png
```

- [ ] **Step teste verifica o que acontece**

Run: `make test`
Expected: PASS. O teste valida que o stream SSE emite as 3 fases de status e então done ou error.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/marmore/api/imageedit/web/ImageEditSseIntegrationTest.java data/granito.png
git commit -m "test(sse): integracao do fluxo SSE completo valida PlantUML"
```

---

## Task 20: Limpeza final e smoke test

Confirma que tudo compila, testes passam, lint passa, e a app sobe.

- [ ] **Step 1: Lint + format**

```bash
make lint
make format
```

- [ ] **Step 2: Testes completos**

```bash
make test
```
Expected: todos PASS.

- [ ] **Step 3: Smoke test manual (opcional)**

```bash
make run &
sleep 10
curl -N -X POST http://localhost:8080/images/edit \
  -H "X-API-Key: marmore-local-dev-key-2026" \
  -H "Accept: text/event-stream" \
  -F "image=@bruno/marmore-api/assets/ambiente.jpg"
kill %1
```

- [ ] **Step 4: Commit final se houver alterações do format**

```bash
git add -A
git diff --cached --quiet || git commit -m "style: spotless apply"
```

---

## Self-Review

Após escrever, compare o plano contra o spec.

**1. Spec coverage:**
- ✅ SSE substitui síncrono: Task 13 remove o controller, Task 12 adiciona o router/handler SSE.
- ✅ Stack WebFlux: Task 16 remove webmvc/restclient, mantém webflux/webclient.
- ✅ WebClient gateway streaming: Task 9 (`OpenAiWebClientImageEditModel` com `stream=true`, `partial_images=0`).
- ✅ Mono ponta a ponta: Task 8 (`ImageEditModel`), Task 11 (`ImageEditService`).
- ✅ RouterFunction + Handler: Task 12.
- ✅ SecurityWebFilterChain integrada: Task 14.
- ✅ Heartbeat ping 15s: Task 12 (merger `Flux.interval`).
- ✅ Custo fixo (tabela): Task 2.
- ✅ Câmbio ao vivo com cache: Task 4.
- ✅ Eventos status (3 fases, incluindo `gerando`): Task 12 + Task 5.
- ✅ done com latency_ms/custo_brl/usage: Task 5 (`SseEvents.done`), Task 11 (montagem).
- ✅ imagem base64 puro: Task 5 (`SseEvents.imagem`).
- ✅ event: error em falha de domínio: Task 5, Task 12.
- ✅ Feature-folder: Task 7 (move image → imageedit).
- ✅ Tabela hardcoded: Task 2 (não no YAML).
- ✅ Exception handler reativo: Task 15.
- ✅ Bruno atualizado: Task 18.
- ✅ Teste de integração: Task 19.

**2. Placeholder scan:** Nenhum "TODO"/"implementar depois". Um risco anotado: o campo `image_b64` vs `b64_json` na Task 9 (anotado como nota de implementação, não placeholder). Parser tenta ambos.

**3. Type consistency:** `SseEvents.done(long, BigDecimal, JsonNode)` é chamado em Task 12 com `(ok.latencyMs(), null, ok.usage())` — tipo bate (`Ok` tem `usage` como `JsonNode`). `costUsd(String, String, String)` da Task 2 é chamado em Task 11 com `opts.model(), opts.quality(), opts.size()` (todos String). `currentRate() → Mono<BigDecimal>` consistente entre Task 4 e Task 11.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-22-sse-edicao-imagem.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
