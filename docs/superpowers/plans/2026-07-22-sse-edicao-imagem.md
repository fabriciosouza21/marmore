# SSE Edição de Imagem — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir o endpoint síncrono `POST /images/edit` por um fluxo SSE reativo que consome o stream da OpenAI (`stream=true`, `partial_images=0`), emite eventos de status/heartbeat/done/imagem e calcula custo em BRL.

**Architecture:** Migração de Spring MVC (servlet) para Spring WebFlux (reativo, Netty). Pipeline reativo ponta a ponta: `FilePart` → resize → gateway WebClient (consome SSE da OpenAI) → `Flux<ServerSentEvent>` para o cliente. Segurança migra de `OncePerRequestFilter` para `SecurityWebFilterChain`. Feature-folder: pacote `image` → `imageedit`.

**Tech Stack:** Java 26, Spring Boot 4.1.0, Spring Security 7 (reativo), WebFlux, WebClient, reactor-core, reactor-test, OkHttp MockWebServer, thumbnailator 0.4.21, Jackson 3 (`tools.jackson.databind`).

**Spec:** `docs/superpowers/specs/2026-07-22-sse-edicao-imagem-design.md`

## Como ler este plano

Este plano descreve **o problema e a API pública esperada** de cada componente. **Não dita a implementação.** Quem implementa é responsável por:

- Pensar na solução, não copiar código que não está aqui.
- Aplicar **Effective Java** consistentemente com o codebase existente:
  - **Item 1 (static factories):** preferir `Foo.of(...)` / `Foo.builder()` a construtores públicos. Os records existentes já seguem isso (`InputImage.of`, `ImageEditPrompt.of`, `AiImageOptions.defaults()`).
  - **Item 2 (builder):** quando um objeto tem muitos parâmetros opcionais, usar o pattern builder (especialmente payloads SSE, opções de imagem).
  - **Item 17 (imutabilidade):** records com cópia defensiva de arrays e listas no construtor canonical (`InputImage`, `ImageResponse` já fazem). Arrays de bytes nunca expostos sem cópia na entrada.
  - **Item 15 (minimize acessibilidade):** campos `private final`, métodos auxiliares `private`, expor só o necessário.
  - **Design de API pública:** pensar no que o consumidor do componente vê. Nome de métodos revela intenção. Assinaturas não vazam detalhes de implementação.
- Seguir Google Java Style (2 espaços, sem tabs, 100 cols). `make format` corrige; `make lint` verifica.
- **TDD:** todo código novo segue RED → GREEN. Escreva o teste que descreve o comportamento esperado primeiro, depois a implementação mínima.
- **Jackson 3:** sempre `tools.jackson.databind.*`, nunca `com.fasterxml`. Serialização JSON sempre via `ObjectMapper`, nunca por concatenação de strings.
- **Reativo:** I/O no event loop. Operações bloqueantes (`resize`, leitura de pedra em disco) em `Schedulers.boundedElastic()`.

## Padrões de referência no codebase

Antes de implementar qualquer componente novo, leia estes arquivos para calibrar o estilo:

- `image/ai/InputImage.java` — record imutável com cópia defensiva de array (Item 17) + static factory `of` (Item 1).
- `image/ai/ImageResponse.java` — record com cópia defensiva de lista + accessor `getResult()`.
- `image/ai/AiImageOptions.java` — record com static factory `defaults()` (Item 1) + método derivado `sendsFidelity()`.
- `image/domain/GenerateResult.java` — sealed interface com `Ok`/`Err` (type-safe, sem exceção).

Estes são a barra de qualidade. Componentes novos devem estar nesse nível.

## Contexto de produto (do Notion)

- **Objetivo do produto:** renderizar bancada de pia suspensa em granito sobre foto de ambiente real. O endpoint recebe a foto do ambiente; prompt fixo e imagem da pedra são injetados pelo backend.
- **Modelo escolhido na rinha:** `gpt-image-2` (flagship, mais barato em `low`: 0.006 USD/imagem, ~32s latência, qualidade suficiente).
- **Pipeline adotado:** `gpt-image-2` em `low` para iteração; `medium`/`high` para final. Neste plano o default passa a ser **`low`** (decisão do usuário, alinhado ao uso majoritário da rinha).
- **`input_fidelity=high`** foi testado e **descartado** (sem diferencial visual, dobra custo). Não enviar.
- **Tabela de preços** (OpenAI jul/2026, USD por imagem): ver spec. Defaults (`gpt-image-2`, `low`, `1024x1024`) = **0.006 USD**.

## Global Constraints

- **Indentação:** 2 espaços, sem tabs, line length 100. Spotless + Checkstyle no pre-commit.
- **Commits:** Conventional Commits. Stagear explicitamente. Um commit por tarefa.
- **Jackson:** `tools.jackson.*` sempre.
- **Modelo/quality default:** `gpt-image-2`, `low`, `1024x1024`. Atualizar `AiImageOptions.defaults()` (hoje `medium`) para `low`.
- **Não há spec para esta seção do produto além do PlantUML e do Notion.** Em caso de dúvida de produto, pergunte antes de assumir.

---

## Task 1: Record de domínio `ImageCost`

**Problema:** Precisamos representar o custo de uma geração (USD, BRL, usage) como um valor imutável que trafega entre a camada de custo e o evento SSE.

**Responsabilidade:** carregar três valores. Nenhuma lógica.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/domain/ImageCost.java`
- Test: `src/test/java/com/marmore/api/imageedit/domain/ImageCostTest.java`

**API pública esperada:**

```
ImageCost(BigDecimal costUsd, BigDecimal costBrl, JsonNode usage)
```

Record imutável. `BigDecimal` para valores monetários (precisão). `JsonNode` para o usage cru da OpenAI (pode ser null).

**Critérios de aceite (testes):**
- Mantém os valores passados.
- Aceita `usage` null sem estourar.

- [ ] **Step 1:** Escreva o teste de comportamento (mantém valores; aceita usage null).
- [ ] **Step 2:** Rode `make test`, confirme FAIL (classe não existe).
- [ ] **Step 3:** Implemente o record seguindo o padrão de `InputImage`/`ImageResponse` (Effective Java Item 17).
- [ ] **Step 4:** Rode `make test`, confirme PASS.
- [ ] **Step 5:** Commit: `feat(cost): adiciona record ImageCost para custo de geracao`

---

## Task 2: `ImageCostCalculator`

**Problema:** Calcular o custo em USD de uma geração a partir do modelo, qualidade e tamanho. A OpenAI cobra por imagem (não por token), então o custo depende apenas desses três parâmetros.

**Responsabilidade:** lookup numa tabela fixa de preços. Sem I/O.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/cost/ImageCostCalculator.java`
- Test: `src/test/java/com/marmore/api/imageedit/cost/ImageCostCalculatorTest.java`

**API pública esperada:**

```
ImageCostCalculator()                                  // sem estado, construido direto
Optional<BigDecimal> costUsd(String model, String quality, String size)
```

Retorna `Optional` vazio se a combinação não existir na tabela.

**Regras de domínio:**
- Tabela hardcoded (mesma fonte da rinha, preços oficiais OpenAI jul/2026). Ver spec para a tabela completa.
- `"auto"` (quality) resolve para `"medium"`. `"auto"` (size) resolve para `"1024x1024"`. Estes são os defaults da OpenAI quando o parâmetro não é explícito.
- Defaults do produto: `gpt-image-2` + `low` + `1024x1024` = **0.006 USD**.

**Critérios de aceite (testes):**
- Defaults do produto retornam 0.006.
- `auto`/`auto` resolve para os defaults (0.006 com low, ou o que a tabela disser para medium).
- Combinação conhecida retorna o valor correto (ex.: `gpt-image-1.5` + `high` + `1024x1536` = 0.200).
- Modelo desconhecido → `Optional.empty()`.
- Quality desconhecida → `Optional.empty()`.

**Notas de design:**
- Classe sem estado (`final`, construtor sem args). Pode ser instanciada direto ou como bean.
- Tabela como estrutura imutável (`Map.of` aninhado ou `Map.copyOf`).

- [ ] **Step 1:** Escreva os testes de comportamento (lookup, auto-resolution, empty cases).
- [ ] **Step 2:** Rode `make test`, confirme FAIL.
- [ ] **Step 3:** Implemente. Aplique Effective Java (imutabilidade da tabela, accessor limpo).
- [ ] **Step 4:** Rode `make test`, confirme PASS.
- [ ] **Step 5:** Commit: `feat(cost): ImageCostCalculator com tabela OpenAI jul/2026`

---

## Task 3: `UsdBrlProperties`

**Problema:** Configurar URL da API de câmbio, TTL do cache e fallback via `application.yaml`.

**Responsabilidade:** carregar config. Sem lógica.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/cost/UsdBrlProperties.java`
- Test: `src/test/java/com/marmore/api/imageedit/cost/UsdBrlPropertiesTest.java`

**API pública esperada:**

```
@ConfigurationProperties(prefix = "marmore.cost.usd-brl")
record UsdBrlProperties(String url, Duration cacheTtl, BigDecimal fallback)
// + construtor compacto com defaults:
//   url = "https://economia.awesomeapi.com.br/json/last/USD-BRL"
//   cacheTtl = Duration.ofHours(6)
//   fallback = new BigDecimal("5.1075")  // Investing 17/07/2026
```

**Critérios de aceite (testes):**
- Construtor sem args entrega os três defaults.

- [ ] **Step 1:** Escreva o teste (defaults presentes).
- [ ] **Step 2:** Rode `make test`, confirme FAIL.
- [ ] **Step 3:** Implemente o record com `@ConfigurationProperties` e construtor de defaults.
- [ ] **Step 4:** Rode `make test`, confirme PASS.
- [ ] **Step 5:** Commit: `feat(cost): UsdBrlProperties com defaults`

---

## Task 4: `UsdBrlProvider`

**Problema:** Buscar a cotação USD→BRL na AwesomeAPI, com cache em memória por TTL para não refazer a chamada a cada request, e fallback hardcoded se a API falhar.

**Responsabilidade:** prover `Mono<BigDecimal> currentRate()` que ou retorna o valor em cache (se válido) ou busca na API.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/cost/UsdBrlProvider.java`
- Test: `src/test/java/com/marmore/api/imageedit/cost/UsdBrlProviderTest.java`

**API pública esperada:**

```
UsdBrlProvider(WebClient.Builder builder, UsdBrlProperties props)
Mono<BigDecimal> currentRate()
```

**Regras de domínio:**
- Cache em memória: se a última busca foi há menos de `cacheTtl`, retorna o valor cacheado sem chamar a API.
- Em erro (HTTP 4xx/5xx, timeout, JSON inválido), usa `fallback`.
- Parse do campo `USDBRL.bid` do JSON da AwesomeAPI.

**Critérios de aceite (testes):**
- Parseia o `bid` da resposta JSON da AwesomeAPI.
- Usa fallback em erro HTTP (500).
- Usa fallback em timeout.
- Não refaz a chamada antes do TTL expirar (segunda chamada dentro do TTL retorna o valor cacheado).
- Após o TTL expirar, refaz a chamada.

**Notas de design:**
- `WebClient.Builder` injetado (não `WebClient` pronto) para que o teste aponte para um `MockWebServer`.
- Cache thread-safe (a API é reativa, múltiplas threads do event loop podem chamar `currentRate` concorrentemente).
- Componente Spring (`@Component`).

**Dependência de teste:** `com.squareup.okhttp3:mockwebserver:4.12.0` (test scope). Adicione ao `pom.xml` se ainda não estiver.

- [ ] **Step 1:** Se necessário, adicione `mockwebserver` ao `pom.xml` e commite separado (`chore(test): adiciona mockwebserver`).
- [ ] **Step 2:** Escreva os testes de comportamento (parse, fallback HTTP, fallback timeout, cache TTL).
- [ ] **Step 3:** Rode `make test`, confirme FAIL.
- [ ] **Step 4:** Implemente. Pense na concorrência do cache (volatile + verificação dupla, ou `AtomicReference`).
- [ ] **Step 5:** Rode `make test`, confirme PASS.
- [ ] **Step 6:** Commit: `feat(cost): UsdBrlProvider com cache TTL e fallback`

---

## Task 5: `SseEvents` (serialização via ObjectMapper)

**Problema:** Construir os payloads JSON dos eventos SSE de forma segura. Nunca por concatenação de strings: isso quebra com `BigDecimal` em notação científica e não escapa strings.

**Responsabilidade:** dado um valor de domínio (fase, metadados de done, erro), produzir um `ServerSentEvent<Object>` com o `data` serializado via Jackson.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/web/SseEvents.java`
- Test: `src/test/java/com/marmore/api/imageedit/web/SseEventsTest.java`

**API pública esperada:**

```
SseEvents(ObjectMapper mapper)                         // ObjectMapper injetado (Jackson 3)

ServerSentEvent<Object> status(String fase)
ServerSentEvent<Object> ping()                         // sem data
ServerSentEvent<Object> done(long latencyMs, BigDecimal custoBrl, JsonNode usage)
ServerSentEvent<Object> imagem(String b64)             // base64 puro, NÃO JSON
ServerSentEvent<Object> error(String error, long latencyMs)
```

**Regras de domínio:**
- Cada payload JSON é um record de payload serializado via `ObjectMapper`. Exemplo: `StatusPayload(String fase)`, `DonePayload(long latency_ms, BigDecimal custo_brl, JsonNode usage)`, `ErrorPayload(String error, long latency_ms)`.
- `imagem` é a exceção: o `data` é o base64 cru, sem envelope JSON (o PlantUML explicita `data: <base64 PNG puro>`).
- `ping` não tem `data`.
- `usage` pode ser null; serializa como `null` no JSON.

**Critérios de aceite (testes):**
- `status` produz JSON válido com a fase (faça parse reverso via `readTree`, não caseie substring).
- `done` produz JSON com `latency_ms`, `custo_brl`, `usage`. Valide via parse reverso.
- `done` com `usage=null` serializa o campo como `null`.
- `custo_brl` como `BigDecimal` preserva escala (ex.: `0.053000` não vira `5.3E-2`). Este teste documenta a razão de usar Jackson.
- `imagem` retorna o base64 cru como `data`.
- `error` escapa aspas na mensagem (ex.: `mensagem com "aspas"`). Valide via parse reverso.

**Notas de design:**
- Componente Spring (`@Component`), não estática, porque precisa do `ObjectMapper` injetado pelo Spring.
- Os records de payload são privados (detalhe de implementação da serialização, não API pública).

- [ ] **Step 1:** Escreva os testes de comportamento. Use parse reverso do JSON para validar.
- [ ] **Step 2:** Rode `make test`, confirme FAIL.
- [ ] **Step 3:** Implemente com records de payload + `ObjectMapper`. Aplique Effective Java (encapsule os records de payload como privados).
- [ ] **Step 4:** Rode `make test`, confirme PASS.
- [ ] **Step 5:** Commit: `feat(sse): SseEvents serializa payloads via ObjectMapper`

---

## Task 6: `ImageEditException` migrada

**Problema:** A exceção de domínio hoje estende `ResponseStatusException` (servlet). No WebFlux precisa ser uma `RuntimeException` com o status HTTP embutido, para o `WebExceptionHandler` traduzir.

**Responsabilidade:** carregar status HTTP + mensagem.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/web/ImageEditException.java`
- Test: `src/test/java/com/marmore/api/imageedit/web/ImageEditExceptionTest.java`

**API pública esperada:**

```
ImageEditException(HttpStatus status, String message)
HttpStatus getStatus()
```

Estende `RuntimeException`. Imutável (campos `final`).

**Critérios de aceite (testes):**
- Carrega status e mensagem.
- É uma `RuntimeException` (pode ser lançada sem declarar).

- [ ] **Step 1:** Escreva o teste.
- [ ] **Step 2:** Rode `make test`, confirme FAIL.
- [ ] **Step 3:** Implemente.
- [ ] **Step 4:** Rode `make test`, confirme PASS.
- [ ] **Step 5:** Commit: `feat(web): ImageEditException reativa`

---

## Task 7: Move pacote `image` → `imageedit` (records puros)

**Problema:** Migrar para feature-folder. Os records e classes puras (sem dependência web/HTTP) movem de `image` para `imageedit`. Sem mudança de lógica.

**Files:** ver `File Structure` no spec. Mova: `ai/*` (exceto `ImageEditModel`, `OpenAiRestClientImageEditModel` que serão reescritos), `domain/*`, `service/ImageResizer`, `io/*`, `config/ImageEditProperties`.

**Como:** use `git mv` para preservar histórico. Atualize `package` e imports.

**Atenção:** esta task deixa o build temporariamente quebrado se feita isoladamente, porque `ImageEditService`, `OpenAiRestClientImageEditModel`, `ImageEditController`, `RestClientConfig` (ainda no pacote velho) referenciam os records movidos. Para manter verde, atualize os imports nesses arquivos também (eles serão reescritos nas tasks seguintes, mas precisam compilar agora).

**Critérios de aceite:**
- `make test` passa.
- `find src -path "*/imageedit/*" -name "*.java"` lista os arquivos movidos.
- Nenhum arquivo `.java` permanece em `com.marmore.api.image.*` (exceto os que serão removidos nas próximas tasks).

- [ ] **Step 1:** `git mv` os arquivos main e test.
- [ ] **Step 2:** Atualize `package` e imports (`sed` ou manual).
- [ ] **Step 3:** Rode `make test`, confirme PASS.
- [ ] **Step 4:** Commit: `refactor(image): move records puros para pacote imageedit`

---

## Task 8: `ImageEditModel` reativo

**Problema:** O gateway hoje é síncrono (`ImageResponse call(prompt)`). No fluxo SSE reativo, precisa retornar `Mono<ImageResponse>` que completa quando a OpenAI responde.

**Responsabilidade:** contrato do gateway reativo.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/ai/ImageEditModel.java`
- Test: `src/test/java/com/marmore/api/imageedit/ai/ImageEditModelTest.java`

**API pública esperada:**

```
@FunctionalInterface
interface ImageEditModel {
  Mono<ImageResponse> call(ImageEditPrompt prompt);
}
```

**Critérios de aceite (testes):**
- Implementação lambda retorna um `Mono`.

- [ ] **Step 1:** Escreva o teste (lambda retorna Mono).
- [ ] **Step 2:** Rode `make test`, confirme FAIL.
- [ ] **Step 3:** Implemente a interface funcional.
- [ ] **Step 4:** Rode `make test`, confirme PASS.
- [ ] **Step 5:** Commit: `feat(ai): interface ImageEditModel reativa`

---

## Task 9: `OpenAiWebClientImageEditModel` (streaming SSE)

**Problema:** O gateway precisa consumir o stream SSE da OpenAI (`POST /v1/images/edits` com `stream=true`, `partial_images=0`), em vez de fazer um POST síncrono esperando JSON. Com `partial_images=0`, a OpenAI emite apenas o evento final `image_generation.completed` com a imagem + usage.

**Responsabilidade:** montar o multipart (com `stream=true` e `partial_images=0`), abrir o stream, parsear o evento `image_generation.completed`, extrair `b64_json`/`image_b64` e `usage`. Falhas viram `AiImageException`.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/ai/OpenAiWebClientImageEditModel.java`
- Test: `src/test/java/com/marmore/api/imageedit/ai/OpenAiWebClientImageEditModelTest.java`

**API pública esperada:**

```
OpenAiWebClientImageEditModel(WebClient webClient)     // bean imageWebClient da Task 10
Mono<ImageResponse> call(ImageEditPrompt prompt)
```

**Regras de domínio:**
- Multipart inclui `prompt`, `stream=true`, `partial_images=0`, e condicionalmente `model`, `n`, `size`, `quality`, `input_fidelity` (como hoje), e `image[]` (preservando ordem: ambiente, pedra).
- Lê o corpo como `Flux<String>` de linhas SSE, filtra por linhas `data:`, identifica o evento `image_generation.completed`.
- Extrai a imagem (`image_b64` ou `b64_json` — valide qual campo real contra uma resposta capturada da OpenAI durante a implementação) e `usage`.
- Erro HTTP (4xx/5xx) durante a abertura ou no stream → `AiImageException`.

**Critérios de aceite (testes com `MockWebServer`):**
- Stream com evento `image_generation.completed` → completa com `ImageResponse` contendo b64 + usage.
- Erro HTTP 500 → `AiImageException`.
- Stream sem evento `completed` (incompleto) → `AiImageException` ou não-completa (decida e documente).

**Notas de design:**
- Preserve a `NamedBytesResource` (inner class que dá nome ao `ByteArrayResource` para o multipart). Reimplemente-a localmente ou extraia para top-level.
- `ObjectMapper` para parse do JSON do evento (não concatenação).

**Risco documentado:** o campo exato da imagem no evento `completed` (`image_b64` vs `b64_json`) pode divergir da documentação. O parser deve tentar ambos. Valide contra uma resposta real durante a implementação.

- [ ] **Step 1:** Escreva os testes com `MockWebServer` (evento completed, erro HTTP).
- [ ] **Step 2:** Rode `make test`, confirme FAIL.
- [ ] **Step 3:** Implemente. Pense no parse do stream SSE (linha a linha vs evento completo).
- [ ] **Step 4:** Rode `make test`, confirme PASS.
- [ ] **Step 5:** Commit: `feat(ai): gateway WebClient consumindo stream SSE da OpenAI`

---

## Task 10: `WebClientConfig`

**Problema:** Substituir o `RestClientConfig` (bean `imageRestClient` com RestClient) por um bean `imageWebClient` com WebClient, mantendo base-url, Bearer auth e read timeout.

**Responsabilidade:** configurar o WebClient do módulo de imagem.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/config/WebClientConfig.java`
- Test: `src/test/java/com/marmore/api/imageedit/config/WebClientConfigTest.java`
- Remove: `image/config/RestClientConfig.java` + teste.
- Remove: `image/ai/OpenAiRestClientImageEditModel.java` + teste (após confirmar que `NamedBytesResource` foi preservada na Task 9).

**API pública esperada:**

```
@Bean WebClient imageWebClient(ImageEditProperties props, WebClient.Builder builder)
```

**Regras de domínio:**
- `baseUrl` = `props.getBaseUrl()`.
- `Authorization: Bearer <apiKey>` como default header.
- Read timeout = `props.getTimeout()` aplicado no Netty (`ReadTimeoutHandler` no `HttpClient`).

**Critérios de aceite (testes):**
- Bean é construído sem erro com properties válidas.

- [ ] **Step 1:** Escreva o teste do bean.
- [ ] **Step 2:** Rode `make test`, confirme FAIL.
- [ ] **Step 3:** Implemente o `WebClientConfig`.
- [ ] **Step 4:** Rode `make test`, confirme PASS.
- [ ] **Step 5:** Remova `RestClientConfig`, `OpenAiRestClientImageEditModel` e seus testes (`git rm`).
- [ ] **Step 6:** Commit: `refactor(config): WebClientConfig substitui RestClientConfig`

---

## Task 11: `ImageEditService` reativo

**Problema:** O service hoje é síncrono (`GenerateResult generate(byte[])`). Precisa retornar `Mono<GenerateResult>` que orquestra validações + resize + chamada ao gateway reativo + cálculo de custo.

**Responsabilidade:** orquestrar o pipeline. Sem I/O direto (delega ao gateway e ao resizer).

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/service/ImageEditService.java`
- Test: `src/test/java/com/marmore/api/imageedit/service/ImageEditServiceTest.java`
- Remove: `image/service/ImageEditService.java` + teste.

**API pública esperada:**

```
ImageEditService(ImageEditProperties, ImageResizer, ImageEditModel, ImageCostCalculator, UsdBrlProvider)
Mono<GenerateResult> generate(byte[] ambiente)
```

**Regras de domínio:**
- Validações síncronas no início (apiKey OpenAI ausente → `Err`; pedra não encontrada em disco → `Err`).
- `resize` (bloqueante) em `Mono.fromCallable(...).subscribeOn(boundedElastic)`.
- Chamada ao gateway reativo (`model.call(prompt) → Mono<ImageResponse>`).
- `latency_ms` reflete apenas o tempo da chamada à OpenAI (envio do request até o `image_generation.completed`).
- Custo: `ImageCostCalculator.costUsd(model, quality, size) × UsdBrlProvider.currentRate()`.
- Nenhum caminho lança: falhas viram `GenerateResult.Err`.

**Critérios de aceite (testes):**
- Sucesso retorna `Ok` com b64 e custo calculado.
- apiKey ausente → `Err` com mensagem começando com "OPENAI_API_KEY".
- Pedra não encontrada → `Err` com "stone image not found".
- Imagem indecodificável → `Err` com "unable to decode".
- Falha do gateway → `Err` com a mensagem da exceção.

**Notas de design:**
- Não bloqueie o event loop com `.block()` dentro do Mono (exceto onde estritamente necessário para custo, e com fallback).
- O `GenerateResult.Ok` pode precisar carregar o custo além do que carrega hoje. Decida se estende o record ou cria um novo valor de domínio.

- [ ] **Step 1:** Escreva os testes de comportamento (sucesso, apiKey ausente, pedra ausente, decode falhou, gateway falhou).
- [ ] **Step 2:** Rode `make test`, confirme FAIL.
- [ ] **Step 3:** Implemente. Componha os Monos.
- [ ] **Step 4:** Rode `make test`, confirme PASS.
- [ ] **Step 5:** Remova o service velho.
- [ ] **Step 6:** Commit: `feat(service): ImageEditService reativo com calculo de custo BRL`

---

## Task 12: `ImageEditHandler` + `ImageEditRouter`

**Problema:** Montar o `Flux<ServerSentEvent>` com a sequência completa do PlantUML: status (recebido → redimensionando → gerando) + heartbeat (ping a cada 15s) + done/imagem (ou error). Expor via `POST /images/edit`.

**Responsabilidade do handler:** orquestrar a sequência de eventos. Responsabilidade do router:** mapear a rota.

**Files:**
- Create: `src/main/java/com/marmore/api/imageedit/web/ImageEditHandler.java`
- Create: `src/main/java/com/marmore/api/imageedit/web/ImageEditRouter.java`
- Test: `src/test/java/com/marmore/api/imageedit/web/ImageEditHandlerTest.java`

**API pública esperada:**

```
// Handler
ImageEditHandler(ImageEditService service, SseEvents events)
Mono<ServerResponse> edit(ServerRequest request)

// Router
@Bean RouterFunction<ServerResponse> imageEditRoute(ImageEditHandler handler)
// rota: POST /images/edit, consumes MULTIPART_FORM_DATA, accept TEXT_EVENT_STREAM
```

**Regras de domínio:**
- Lê o `FilePart` "image" do multipart, converte para bytes.
- Emite `status recebido` → `status redimensionando` → `status gerando`.
- Enquanto a geração não completa, emite `ping` a cada 15s.
- Ao completar: `done` + `imagem` (se Ok) ou `error` (se Err).
- Fecha o stream após o resultado.
- O heartbeat para quando o resultado chega (não continua emitindo ping após done/imagem).

**Critérios de aceite (testes):**
- Stream com sucesso emite a sequência: status×3 → done → imagem.
- Stream com erro de domínio emite: status×3 → error.
- O ping só é emitido se a geração demorar (use `StepVerifier.withVirtualTime` para avançar o relógio sem esperar 15s reais).

**Notas de design (pense na composição reativa):**
- Como fundir o heartbeat com o resultado? O `Flux.interval(15s)` precisa ser cancelado quando o `Mono` do service completa. Pense em `Flux.merge` com `takeUntil`, ou `Mono.delayUntil`, ou outro operador. A escolha é sua; o teste valida o comportamento.
- A leitura dos bytes do `FilePart` é reativa (`DataBufferUtils.join` + liberação do buffer).

- [ ] **Step 1:** Escreva os testes de comportamento (sequência de sucesso, sequência de erro, heartbeat com virtual time).
- [ ] **Step 2:** Rode `make test`, confirme FAIL.
- [ ] **Step 3:** Implemente o handler. Pense no merger heartbeat↔resultado.
- [ ] **Step 4:** Implemente o router.
- [ ] **Step 5:** Rode `make test`, confirme PASS.
- [ ] **Step 6:** Commit: `feat(web): handler e router SSE para /images/edit`

---

## Task 13: Remove o controller MVC velho

**Problema:** O controller síncrono (`ImageEditController`, `@RestController`) não tem mais lugar. O SSE o substituiu.

**Files:**
- Remove: `image/web/ImageEditController.java` + testes (`ImageEditControllerTest`, `ImageUploadSizeTest`).

- [ ] **Step 1:** `git rm` os arquivos.
- [ ] **Step 2:** Verifique que `com/marmore/api/image/` está vazio (todos migrados ou removidos).
- [ ] **Step 3:** Rode `make test`, confirme PASS (pode haver erros de compilação por `spring-boot-starter-webmvc` ainda presente — resolvidos na Task 16).
- [ ] **Step 4:** Commit: `refactor(web): remove controller MVC sincrono /images/edit`

---

## Task 14: Segurança reativa (`ApiKeyAuthWebFilter` + `SecurityConfiguration`)

**Problema:** A autenticação hoje é servlet (`OncePerRequestFilter`). Precisa virar reativa (`WebFilter` integrado à `SecurityWebFilterChain`).

**Files:**
- Create: `src/main/java/com/marmore/api/security/ApiKeyAuthWebFilter.java`
- Modify: `src/main/java/com/marmore/api/security/SecurityConfiguration.java`
- Create: `src/test/java/com/marmore/api/security/ApiKeyAuthWebFilterTest.java`
- Remove: `ApiKeyAuthFilter.java` + teste.

**API pública esperada:**

```
// Filtro
ApiKeyAuthWebFilter(ApiKeyProperties props)
// implementa WebFilter; HEADER = "X-API-Key"

// SecurityConfig (reativo)
@EnableWebFluxSecurity
@Bean SecurityWebFilterChain filterChain(ServerHttpSecurity http, ApiKeyAuthWebFilter apiKeyFilter)
```

**Regras de domínio:**
- Compara a chave do header com a configurada via `MessageDigest.isEqual` (comparação constante, anti-timing). Mesma lógica do filtro atual.
- Chave válida → autentica e continua a chain.
- Ausente/inválida → 401 com body JSON `{"error":"API key ausente ou invalida"}`.
- CSRF desabilitado, stateless (`NoOpServerSecurityContextRepository`), basic/form desabilitados.

**Critérios de aceite (testes):**
- Key válida → request passa (chain continua).
- Key ausente → 401.
- Key inválida → 401.

- [ ] **Step 1:** Escreva os testes do filtro (válido, ausente, inválido).
- [ ] **Step 2:** Rode `make test`, confirme FAIL.
- [ ] **Step 3:** Implemente o `WebFilter`.
- [ ] **Step 4:** Reescreva o `SecurityConfiguration` para reativo (`ServerHttpSecurity`, `@EnableWebFluxSecurity`).
- [ ] **Step 5:** Rode `make test`, confirme PASS.
- [ ] **Step 6:** Remova `ApiKeyAuthFilter` + teste.
- [ ] **Step 7:** Commit: `feat(security): autenticacao X-API-Key reativa`

---

## Task 15: Exception handler reativo

**Problema:** O `@RestControllerAdvice` (servlet) não funciona no WebFlux. Precisa de um `WebExceptionHandler` reativo.

**Files:**
- Create (sobrescreve): `src/main/java/com/marmore/api/web/GlobalWebExceptionHandler.java`
- Create: `src/test/java/com/marmore/api/web/GlobalWebExceptionHandlerTest.java`

**API pública esperada:**

```
@Component @Order(-2)
class GlobalWebExceptionHandler implements WebExceptionHandler
Mono<Void> handle(ServerWebExchange exchange, Throwable ex)
```

**Regras de domínio:**
- `ImageEditException` → responde com o status embutido na exceção.
- `ResponseStatusException` (do Spring) → responde com o status da exceção.
- Outras exceções → 500 com mensagem genérica.

**Critérios de aceite (testes):**
- `ImageEditException` com status 503 → resposta 503.
- Exceção genérica → 500.

- [ ] **Step 1:** Escreva o teste.
- [ ] **Step 2:** Rode `make test`, confirme FAIL.
- [ ] **Step 3:** Implemente.
- [ ] **Step 4:** Rode `make test`, confirme PASS.
- [ ] **Step 5:** Commit: `feat(web): exception handler reativo`

---

## Task 16: Migração de stack — `pom.xml`

**Problema:** O `pom.xml` hoje tem `webmvc` + `webflux` + `restclient` + `webclient` coexistindo. Para o WebFlux puro, remova `webmvc` e `restclient` (+ test starters).

**Files:** `pom.xml`

- [ ] **Step 1:** Remova `spring-boot-starter-webmvc`, `spring-boot-starter-restclient`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-restclient-test`. Mantenha `webflux`, `webclient`, `security`, `data-jpa`, `jdbc`, `thumbnailator`, `spring-ai-starter-model-openai`, `postgresql`, `h2`. Adicione `io.projectreactor:reactor-test` (test scope) se ainda não estiver.
- [ ] **Step 2:** Rode `make test`, confirme PASS.
- [ ] **Step 3:** Commit: `chore(build): remove webmvc e restclient; full webflux`

---

## Task 17: `application.yaml` + `ApiApplication`

**Problema:** Adicionar config de custo/câmbio, remover config de multipart servlet (inútil no WebFlux), registrar `UsdBrlProperties`.

**Files:** `application.yaml` (main + test), `ApiApplication.java`.

- [ ] **Step 1:** Em `application.yaml`, adicione sob `marmore`:

```yaml
  cost:
    usd-brl:
      url: https://economia.awesomeapi.com.br/json/last/USD-BRL
      cache-ttl: 6h
      fallback: 5.1075
```

Troque `spring.servlet.multipart` por `spring.codec.max-in-memory-size: 25MB` (limite in-memory do WebFlux).

- [ ] **Step 2:** Atualize `ApiApplication` para registrar `UsdBrlProperties.class` no `@EnableConfigurationProperties`.
- [ ] **Step 3:** Rode `make test`, confirme PASS.
- [ ] **Step 4:** Rode `make lint`, confirme 0 violations.
- [ ] **Step 5:** Commit: `chore(config): adiciona marmore.cost, codec, registra UsdBrlProperties`

---

## Task 18: Atualizar default de quality para `low`

**Problema:** `AiImageOptions.defaults()` hoje usa `quality=medium` (0.053 USD). Decisão do usuário: passar para `low` (0.006 USD), alinhado ao uso majoritário da rinha e ao objetivo de protótipo de baixo custo.

**Files:** `imageedit/ai/AiImageOptions.java` (já migrado na Task 7).

- [ ] **Step 1:** Atualize `defaults()` de `medium` para `low`.
- [ ] **Step 2:** Atualize o teste existente de `AiImageOptionsTest` para refletir o novo default.
- [ ] **Step 3:** Rode `make test`, confirme PASS.
- [ ] **Step 4:** Commit: `feat(ai): quality default passa a ser low (alinhado a rinha)`

---

## Task 19: Bruno collection

**Problema:** O `.bru` existente não declara `Accept: text/event-stream`.

**Files:** `bruno/marmore-api/editar imagem.bru`

- [ ] **Step 1:** Adicione `Accept: text/event-stream` aos headers do `.bru`.
- [ ] **Step 2:** Commit: `docs(bruno): request SSE com Accept text/event-stream`

---

## Task 20: Teste de integração do fluxo SSE completo

**Problema:** Provar que o PlantUML foi implementado end-to-end. Sobe o contexto WebFlux, mocka o gateway da OpenAI, envia multipart via `WebTestClient`, valida a sequência de eventos SSE.

**Files:**
- Create: `src/test/java/com/marmore/api/imageedit/web/ImageEditSseIntegrationTest.java`

**Critérios de aceite (testes):**
- `POST /images/edit` com multipart + `X-API-Key` + `Accept: text/event-stream` retorna 200 SSE.
- O stream emite a sequência: status (recebido/redimensionando/gerando) → done/imagem (ou error).
- Sem `X-API-Key` → 401.

**Notas de design:**
- Use `WebTestClient` + `StepVerifier` sobre o body reativo.
- Mocke o `ImageEditModel` (via `@TestConfiguration` + `@Primary`) para retornar um `ImageEditResponse` fixo.
- Mocke o `UsdBrlProvider` para não chamar a AwesomeAPI real.

- [ ] **Step 1:** Escreva o teste de integração.
- [ ] **Step 2:** Se `data/granito.png` não existir para o teste, crie um stub.
- [ ] **Step 3:** Rode `make test`, confirme PASS.
- [ ] **Step 4:** Commit: `test(sse): integracao do fluxo SSE completo valida PlantUML`

---

## Task 21: Smoke test final

**Problema:** Confirmar que tudo compila, testes passam, lint passa, e a app sobe.

- [ ] **Step 1:** `make lint && make format && make test`. Tudo PASS.
- [ ] **Step 2:** Smoke test manual (opcional): suba a app, mande um request real com a foto do ambiente do Bruno, observe o stream SSE.
- [ ] **Step 3:** Commit final se `make format` alterou algo.

---

## Self-Review

**Spec coverage:** cada seção do spec mapeia para uma tarefa.
- SSE substitui síncrono: Task 13 remove controller, Task 12 adiciona router/handler.
- Stack WebFlux: Task 16.
- WebClient streaming: Task 9.
- Mono ponta a ponta: Task 8, 11.
- RouterFunction + Handler: Task 12.
- SecurityWebFilterChain: Task 14.
- Heartbeat ping 15s: Task 12.
- Custo fixo: Task 2.
- Câmbio ao vivo com cache: Task 4.
- Eventos status (3 fases): Task 5, 12.
- done com latency/custo/usage: Task 5, 11.
- imagem base64 puro: Task 5.
- event: error: Task 5, 12.
- Feature-folder: Task 7.
- Tabela hardcoded: Task 2.
- Exception handler reativo: Task 15.
- Bruno: Task 19.
- Quality low: Task 18 (adicionado após feedback do Notion).
- Integração: Task 20.

**Decisões que mudaram durante o brainstorming (registradas no plano):**
- `quality` default: `medium` → `low` (Task 18), alinhado à rinha e à decisão do usuário.
- Modelo: `gpt-image-2` (confirmado, apesar da contradição no overview do Notion).

**O que este plano NÃO faz (por design):** ditar código. Cada tarefa descreve o problema, a API pública e os critérios de aceite. A implementação é responsabilidade de quem executa, aplicando Effective Java e os padrões do codebase.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-22-sse-edicao-imagem.md`. Two execution options:

**1. Subagent-Driven (recommended)** — Despacho um subagent novo por task, reviso entre tasks, iteração rápida.

**2. Inline Execution** — Executo as tasks nesta sessão com executing-plans, em batch com checkpoints.

Qual abordagem?
