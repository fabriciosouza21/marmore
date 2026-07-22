# Design: edição de imagem via SSE (Server-Sent Events)

**Data:** 2026-07-22
**Branch:** `feature/endpoint-edicao-imagem`
**Status:** draft

## Contexto

O endpoint `POST /images/edit` hoje é síncrono: recebe a foto do ambiente
(multipart), chama o `/v1/images/edits` da OpenAI (bloqueante, até 180s) e
devolve o PNG resultante. Durante a espera, o cliente fica sem qualquer
feedback de progresso.

O PlantUML `docs/c4-model/dynamic/sse-user-flow.puml` formaliza um novo fluxo:
o mesmo endpoint passa a responder via SSE (Server-Sent Events), emitindo
eventos de status durante o processamento, heartbeat enquanto a OpenAI
responde, e por fim o resultado com metadados de custo e a imagem em base64.

O ponto central é dissociar a chamada bloqueante da OpenAI do envio de
eventos ao cliente. Isso muda o modelo de execução da aplicação.

## Objetivo

Substituir o endpoint síncrono `POST /images/edit` por um fluxo SSE que:

1. Abre canal unidirecional (servidor envia, cliente só escuta).
2. Emite eventos de status (`recebido`, `redimensionando`, `gerando`).
3. Envia `ping` a cada 15s enquanto a OpenAI processa (até 180s).
4. Ao receber a resposta, emite `done` com metadados e `imagem` com o base64.
5. Em caso de erro de domínio, emite `error` e fecha o stream.

## Decisões

| Decisão | Escolha | Razão |
|---|---|---|
| Coexistência síncrono vs SSE | SSE **substitui** o síncrono | Reduz surface area. Cliente consome só o stream. |
| Custo BRL | Tabela fixa por `model × quality × size` (OpenAI jul/2026) | A `usage` da OpenAI não determina custo (preço é por imagem, não por token). Tabela estática basta. |
| Câmbio USD→BRL | Ao vivo (AwesomeAPI) com cache em memória + fallback 5.1075 | Valor aproximado do real sem chamada a cada request. |
| Heartbeat | `event: ping` a cada 15s durante a espera da OpenAI | Mantém conexão viva para proxies/LBs e dá feedback ao cliente. |
| Stack | Full WebFlux reativo (migra `spring-boot-starter-web` → `webflux`) | Modelo reativo ponta a ponta, idiomático para SSE+heartbeat. |
| Cliente OpenAI | WebClient reativo (migra `RestClient` → `WebClient`) | Alinha o gateway com o stack reativo. |
| Web layer | RouterFunction + HandlerFunction | Estilo funcional WebFlux puro. |
| Segurança | `SecurityWebFilterChain` integrada (reativa) | Idiomático do Spring Security WebFlux. |
| Tabela de preços | Hardcoded no `ImageCostCalculator` | Simples. Preços da OpenAI mudam raramente. |
| Pacotes | Feature-folder por feature raiz | `imageedit/` com subpastas por camada. `security/` e `web/` transversais na raiz. |

## Eventos SSE

Canal `text/event-stream` unidirecional. Eventos emitidos em ordem:

| # | Event | data (JSON) | Quando |
|---|---|---|---|
| 1 | `status` | `{"fase":"recebido"}` | imediato após abrir o canal |
| 2 | `status` | `{"fase":"redimensionando"}` | antes de chamar o `ImageResizer` |
| 3 | `status` | `{"fase":"gerando"}` | antes de chamar a OpenAI |
| 4 | `ping` | *(nenhum data)* | a cada 15s enquanto a OpenAI responde |
| 5 | `done` | `{"latency_ms":N,"custo_brl":N,"usage":{...}}` | ao receber 200 da OpenAI |
| 6 | `imagem` | `<base64 PNG puro, não JSON>` | logo após o done |
| — | *(fim do stream)* | — | após imagem |

Em erro de domínio (key OpenAI ausente, pedra não encontrada, falha de decode,
falha na geração), substitui `done`+`imagem` por:

| # | Event | data (JSON) | Quando |
|---|---|---|---|
| 5' | `error` | `{"error":"...","latency_ms":N}` | falha de domínio |

O `data` do evento `imagem` é o base64 cru do PNG, sem envelope JSON e sem
prefixo `data:image/png;base64,`.

## Arquitetura

Migração de servlet (blocking, thread-per-request) para reativo (event loop
Netty, non-blocking). O fluxo de edição de imagem vira um pipeline reativo
ponta a ponta.

```
Cliente POST /images/edit (multipart reativo, FilePart)
   │
   ▼
WebFilter chain (Netty)
   └── SecurityWebFilterChain (Spring Security reativo)
         ├── X-API-Key validado via AuthenticationWebFilter / addFilterAt
         │     comparação constante (MessageDigest.isEqual) anti-timing
         │     inválido → ServerResponse.status(401).json({error: ...})
         │
   ▼
RouterFunction: POST /images/edit
   consumes MULTIPART_FORM_DATA, Accept: text/event-stream
   │
   ▼
ImageEditHandler.edit(ServerRequest) → Mono<ServerResponse>
   retorna body(Flux<ServerSentEvent<Object>>, TEXT_EVENT_STREAM)
   │
   ▼
Pipeline reativo:
   1. ler FilePart "image" → byte[] (DataBufferUtils)
   2. emit status {fase: recebido}
   3. emit status {fase: redimensionando}
      resize via Mono.fromCallable(resizer::resize).subscribeOn(boundedElastic)
   4. emit status {fase: gerando}
   5. merger:
      - Flux.interval(Duration.ofSeconds(15)).map → event: ping
      - ImageEditService.generate(bytes) → done + imagem (ou error)
      merged; ping cancelado ao completar a geracao
   6. close stream
```

## Camada de domínio/serviço

O `ImageEditService.generate(byte[])` passa a retornar `Mono<GenerateResult>`.
O gateway `ImageEditModel.call(prompt)` passa a retornar
`Mono<ImageResponse>`. Os records de dados (`ImageEditPrompt`,
`AiImageOptions`, `InputImage`, `ImageResponse`, `GenerateResult`) seguem
idênticos, pois são puros dados.

Composição do `Mono<GenerateResult>` no service:

- Validações síncronas (apiKey, pedra em disco) no início do Mono.
- `resize` via `Mono.fromCallable(...).subscribeOn(boundedElastic)`.
- `model.call(prompt)` retorna o `Mono<ImageResponse>` reativo.
- Latência medida envolvendo o `model.call` com `System.nanoTime()`. O
  `latency_ms` do evento `done` reflete apenas o tempo da chamada à OpenAI
  (envio do request até recebimento do JSON), não inclui resize ou leitura do
  upload.

## Cálculo de custo

Dois componentes isolados.

**`ImageCostCalculator`**: tabela `PRICE_PER_IMAGE` hardcodeada (model ×
quality × size, preços oficiais OpenAI jul/2026, mesma fonte da rinha).
Lookup resolve `auto` → `1024x1024` e `auto` → `medium`. Retorna o custo em
USD por imagem, ou `null` se a combinação não existir na tabela.

Tabela de referência (USD por imagem):

```
gpt-image-2:
  low:    {1024x1024: 0.006, 1024x1536: 0.005, 1536x1024: 0.005}
  medium: {1024x1024: 0.053, 1024x1536: 0.041, 1536x1024: 0.041}
  high:   {1024x1024: 0.211, 1024x1536: 0.165, 1536x1024: 0.165}
gpt-image-1.5:
  low:    {1024x1024: 0.009, 1024x1536: 0.013, 1536x1024: 0.013}
  medium: {1024x1024: 0.034, 1024x1536: 0.050, 1536x1024: 0.050}
  high:   {1024x1024: 0.133, 1024x1536: 0.200, 1536x1024: 0.200}
gpt-image-1-mini:
  low:    {1024x1024: 0.005, 1024x1536: 0.006, 1536x1024: 0.006}
  medium: {1024x1024: 0.011, 1024x1536: 0.015, 1536x1024: 0.015}
  high:   {1024x1024: 0.036, 1024x1536: 0.052, 1536x1024: 0.052}
```

Defaults atuais (`gpt-image-2`, `medium`, `1024x1024`) → **0.053 USD**.

**`UsdBrlProvider`**: busca USD→BRL na AwesomeAPI
(`https://economia.awesomeapi.com.br/json/last/USD-BRL`), com cache em
memória por TTL configurável (default 6h). Em caso de timeout/erro, usa
fallback hardcodeado 5.1075 (cotação Investing 17/07/2026). Parse do campo
`USDBRL.bid`.

`custo_brl = cost_usd × usd_brl`, ambos como `BigDecimal` para precisão
monetária.

## Estrutura de pacotes (feature-folder por feature raiz)

```
com.marmore.api
├── ApiApplication.java
│
├── imageedit/                      ← FEATURE: edição de imagem
│   ├── web/
│   │   ├── ImageEditRouter.java
│   │   ├── ImageEditHandler.java
│   │   ├── SseEvents.java
│   │   └── ImageEditException.java
│   ├── service/
│   │   ├── ImageEditService.java
│   │   └── ImageResizer.java
│   ├── ai/
│   │   ├── ImageEditModel.java              (call → Mono<ImageResponse>)
│   │   ├── OpenAiWebClientImageEditModel.java
│   │   ├── ImageEditPrompt.java
│   │   ├── AiImageOptions.java
│   │   ├── InputImage.java
│   │   ├── ImageResponse.java
│   │   ├── ImageResponseMetadata.java
│   │   ├── ImageGeneration.java
│   │   ├── Image.java
│   │   └── AiImageException.java
│   ├── domain/
│   │   ├── GenerateResult.java
│   │   ├── EditPrompts.java
│   │   └── ImageCost.java
│   ├── cost/
│   │   ├── ImageCostCalculator.java
│   │   ├── UsdBrlProvider.java
│   │   └── UsdBrlProperties.java
│   ├── io/
│   │   ├── ImageResultWriter.java
│   │   └── FileSystemResultWriter.java
│   └── config/
│       ├── ImageEditProperties.java
│       └── WebClientConfig.java
│
├── security/                       ← TRANSVERSAL
│   ├── ApiKeyAuthWebFilter.java
│   ├── ApiKeyProperties.java
│   └── SecurityConfiguration.java
│
└── web/                            ← TRANSVERSAL
    └── GlobalWebExceptionHandler.java
```

Mudança de nome: pacote `image` → `imageedit`. Todos os imports
`com.marmore.api.image.*` migram para `com.marmore.api.imageedit.*`.

## Mapeamento antigo → novo

| Antes (MVC, servlet) | Depois (WebFlux, reativo) |
|---|---|
| `ApiKeyAuthFilter extends OncePerRequestFilter` | `ApiKeyAuthWebFilter` (reactor, integrado à `SecurityWebFilterChain`) |
| `SecurityConfiguration` (Spring Security servlet, `HttpSecurity`) | `SecurityConfiguration` (Spring Security reativo, `ServerHttpSecurity`) |
| `ImageEditController` (`@RestController`) | `ImageEditRouter` (RouterFunction) + `ImageEditHandler` |
| `@RequestParam MultipartFile image` | `FilePart` (reativo, lido via `DataBufferUtils`) |
| `OpenAiRestClientImageEditModel` (`RestClient`) | `OpenAiWebClientImageEditModel` (`WebClient`) |
| `ImageEditModel.call(prompt) → ImageResponse` | `ImageEditModel.call(prompt) → Mono<ImageResponse>` |
| `ImageEditService.generate(byte[]) → GenerateResult` | `ImageEditService.generate(byte[]) → Mono<GenerateResult>` |
| `@ControllerAdvice GlobalExceptionHandler` | `WebExceptionHandler` |
| `ImageEditException extends ResponseStatusException` | `ImageEditException extends RuntimeException` |
| `RestClientConfig` (bean `imageRestClient`) | `WebClientConfig` (bean `webClient` / `imageWebClient`) |
| `spring-boot-starter-web` | `spring-boot-starter-webflux` |

## Configuração

**`pom.xml`**:

- Substituir `spring-boot-starter-web` → `spring-boot-starter-webflux`.
- `spring-boot-starter-security` cobre o modo reativo (mesmo artefato serve
  para servlet e reativo; no classpath reativo auto-configura
  `SecurityWebFilterChain`).
- Adicionar `io.projectreactor:reactor-test` (test scope) para
  `StepVerifier.withVirtualTime()`.
- Adicionar `com.squareup.okhttp3:mockwebserver` (test scope) para testar o
  WebClient da OpenAI e o `UsdBrlProvider`.

**`application.yaml`** (acrescentar sob `marmore`):

```yaml
marmore:
  api:
    key: ${MARMORE_API_KEY:}
  openai:
    image:
      base-url: https://api.openai.com
      api-key: ${OPENAI_API_KEY:}
      default-model: gpt-image-2
      timeout: 180s
      stone-path: ${user.dir}/data/granito.png
  cost:                              # NOVO
    usd-brl:
      url: https://economia.awesomeapi.com.br/json/last/USD-BRL
      cache-ttl: 6h
      fallback: 5.1075               # Investing 17/07/2026
```

A tabela de preços fica hardcoded no `ImageCostCalculator` (não vai no YAML).

## Tratamento de erro

| Cenário | Comportamento |
|---|---|
| API key OpenAI ausente | `error` event `{"error":"OPENAI_API_KEY ausente...","latency_ms":N}` |
| Pedra (granito.png) não encontrada em disco | `error` event `{"error":"stone image not found...","latency_ms":N}` |
| Foto do ambiente indecodificável | `error` event `{"error":"unable to decode input image...","latency_ms":N}` |
| Falha HTTP na chamada à OpenAI | `error` event `{"error":"...","latency_ms":N}` |
| Erro não previsto | `error` event genérico `{"error":"erro interno","latency_ms":N}` |
| X-API-Key ausente/inválida | 401 antes de abrir o stream (erro de transporte) |
| Upload excede 25MB | 413 antes de abrir o stream (limite multipart do Netty/WebFlux, configurado em `spring.codec.max-in-memory-size` e `spring.servlet.multipart` equivalente) |
| Erro de rede durante o stream | O `Flux` completa erroneamente; o cliente percebe o fechamento abrupto |

## Testes

| Componente | Ferramenta | O que valida |
|---|---|---|
| `ImageEditRouter` + `ImageEditHandler` | `WebTestClient` + `StepVerifier` sobre `Flux<ServerSentEvent>` | sequência de eventos (recebido → redimensionando → gerando → done → imagem), ping emitido via `withVirtualTime`, `error` em falha de domínio |
| `ImageEditService` | JUnit + `StepVerifier` sobre `Mono<GenerateResult>` | Ok com b64 válido, Err para key ausente / pedra / decode, latência medida |
| `OpenAiWebClientImageEditModel` | `MockWebServer` (OkHttp) | multipart correto, parse do `b64_json`, erro HTTP vira `AiImageException` |
| `ImageCostCalculator` | JUnit puro | lookup por model×quality×size, fallback, `auto` → 1024x1024/medium |
| `UsdBrlProvider` | JUnit + `MockWebServer` | busca AwesomeAPI, parse `USDBRL.bid`, fallback em timeout/erro, cache TTL não refaz antes de expirar |
| `ApiKeyAuthWebFilter` / `SecurityConfiguration` | `WebTestClient` | 401 sem header, 401 com key inválida, 200 com key válida, comparação constante |
| `GlobalWebExceptionHandler` | `WebTestClient` | 413 para upload grande |
| Fluxo SSE completo (integração) | `WebTestClient` + mock `WebClient` OpenAI | sequência completa de eventos SSE, provando o PlantUML |

**Pontos de atenção**:

1. **Heartbeat com tempo virtual**: o `Flux.interval(15s)` é testado via
   `StepVerifier.withVirtualTime()` para avançar o relógio artificialmente,
   sem esperar 15s reais.
2. **Multipart reativo no WebTestClient**: suportado via builder
   `.multipartBuilder()` (Spring 6.1+). Upload da foto do ambiente simulado
   com `ByteArrayResource` ou arquivo em `src/test/resources/`.
3. **MockWebServer vs MockRestServiceServer**: o MVC usava
   `MockRestServiceServer` (servlet). No WebFlux o equivalente é
   `okhttp3.mockwebserver.MockWebServer`, padrão para testar WebClient.

## Fora de escopo

- Streaming real da geração na OpenAI: o PlantUML explicita que a OpenAI não
  faz streaming da geração. O "progresso" é apenas heartbeat, não evolução
  real.
- Autenticação por OAuth/JWT: segue apenas `X-API-Key`.
- Persistência do resultado: o `FileSystemResultWriter` existe mas não é
  usado pelo path HTTP (fica para uso futuro/testes).
- WebSocket: o fluxo é SSE unidirecional, sem necessidade de canal bidirecional.

## Riscos

1. **Migração de stack**: a troca para WebFlux toca quase toda a app
   (security, multipart, exception handler, controller, service, gateway,
   tests). É uma mudança maior do que o fluxo SSE isoladamente. O
   `SseEmitter` no MVC (alternativa considerada) entregaria o mesmo
   SSE+heartbeat com ruptura mínima. A escolha do WebFlux foi uma decisão
   consciente do usuário após os trade-offs serem apresentados.
2. **`.env` versionado**: o `.env` com `OPENAI_API_KEY` real continua
   versionado. Não é bloqueante para a implementação, mas é um risco de
   segurança preexistente que vale sanar separadamente.
3. **Timeout do câmbio**: a AwesomeAPI pode estar indisponível. O fallback
   garante que o fluxo não quebra, mas o `custo_brl` pode ficar desatualizado
   por até `cache-ttl` (6h) após a cotação real mudar. Aceitável.
4. **Preços desatualizados**: a tabela hardcoded reflete jul/2026. Se a
   OpenAI revisar, o `custo_brl` fica incorreto até atualização manual no
   código. Aceitável (revisão trimestral+).
