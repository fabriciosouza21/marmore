# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
e este projeto adere ao [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [Não publicado]

### Adicionado

- Endpoint reativo `POST /images/edit` (WebFlux): recebe `image` (multipart) e
  devolve um stream SSE (`text/event-stream`) com eventos `status`, `ping`
  (heartbeat a cada 15s), `done` (latência + custo BRL + usage) e `imagem`
  (base64) ou `error`. Autenticação por header `X-API-Key`.
- Gateway reativo da OpenAI (`OpenAiWebClientImageEditModel`) consumindo o
  stream SSE de `/v1/images/edits` com tradução de erro-no-200 e timeout de
  leitura aplicado no Netty.
- `ImageResizer` redimensionando para no máximo 1536px (maior lado) com saída
  JPEG 0.85 via Thumbnailator; rejeita imagens com dimensão acima de 4096px
  antes de decodificar o raster (anti-bomba de descompressão).
- `EditPrompts` com prompt fixo de bancada (port de
  `openai_image/prompt.md`).
- Propriedade `marmore.openai.image.stone-path` apontando para a imagem da
  pedra no disco; `marmore.openai.image.api-key` e `stone-path` validados em
  startup (fail-fast).
- Calculadora de custo em USD (tabela OpenAI jul/2026) e provedor de cotação
  USD->BRL (`UsdBrlProvider`) com cache TTL (6h), fallback e cache negativo
  (30s) em falha da AwesomeAPI.
- `GlobalWebExceptionHandler` reativo traduzindo exceções em JSON
  `{"error":"..."}` preservando o status, sem vazar detalhe interno.
- Checkstyle 11.0.1 com configuração do Google Java Style.
- Spotless com `google-java-format` para formatação automática.
- `Makefile` com alvos `lint`, `format`, `test`, `verify` e `clean`.
- Hook `pre-commit` versionado em `.githooks/` que executa `make lint`.
- `setup.sh` para ativar o `hooksPath` do git após clone.

### Alterado

- **BREAKING:** `POST /images/edit` agora recebe apenas `image` (multipart
  singular). Os parâmetros `prompt` e a segunda `images` foram removidos; o
  prompt fixo e a imagem da pedra são injetados pelo backend.
- Stack sincrona (servlet + `RestClient`) substituida pela reativa (WebFlux +
  `WebClient`); respostas viram stream SSE em vez de PNG binario com status
  HTTP de erro.
- Limite de upload em memoria definido via `spring.codec.max-in-memory-size`
  (25MB).

### Removido

- Parâmetro `prompt` do request.
- Parâmetro `images` (lista) do request; substituído por `image` singular.
- Controller MVC sincrono, `RestClient` e tipos associados.

## [0.1.0] - 2026-07-20

### Adicionado
- Inicialização do projeto.
