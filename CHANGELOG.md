# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
e este projeto adere ao [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [Não publicado]

### Alterado

- **BREAKING:** `POST /images/edit` agora recebe apenas `image` (multipart
  singular). Os parâmetros `prompt` e a segunda `images` foram removidos; o
  prompt fixo e a imagem da pedra são injetados pelo backend.

### Adicionado

- `EditPrompts` com prompt fixo de bancada (port de
  `openai_image/prompt.md`).
- `ImageResizer` redimensionando para no máximo 1536px (maior lado) com
  saída JPEG 0.85 via Thumbnailator.
- Propriedade `marmore.openai.image.stone-path` apontando para a imagem da
  pedra no disco.
- Limite de upload aumentado para 25MB (`spring.servlet.multipart`).

### Removido

- Parâmetro `prompt` do request.
- Parâmetro `images` (lista) do request; substituído por `image` singular.

### Adicionado
- Endpoint `POST /images/edit` (multipart): recebe `prompt` + `images[]` e devolve o PNG
  resultante com `Content-Type: image/png` (200). Erros viram 503 (api-key ausente),
  400 (imagem de entrada ausente) ou 502 (falha na OpenAI).
- `ImageEditController` e `ImageEditException` (carrega `HttpStatus` por tipo de erro).
- Módulo de edição de imagem (`com.marmore.api.image`) com porta do `openai_image` em Python.
- `ImageEditService.generate()`: chamada ao endpoint `POST /v1/images/edits` via `RestClient`,
  com multipart e `input_fidelity` condicional. Nenhum caminho lança exceção; falhas viram
  `GenerateResult.Err`.
- Tipos de domínio: `GenerateResult` (sealed `Ok`/`Err`) e `EditOptions` (com defaults e
  `sendsFidelity()`).
- `ImageEditProperties` (`@ConfigurationProperties(prefix = "marmore.openai.image")`) e bean
  `RestClient` autenticado com `Authorization: Bearer`.
- `FileSystemResultWriter` grava PNG (base64 decodificado) e JSON cru em disco, criando o
  diretório se necessário; lança `IllegalStateException` em resultado de erro.
- Cobertura de testes para os 12 casos do contrato (4 de domínio, 1 de config, 1 de bean,
  9 de service, 2 de writer), com `MockRestServiceServer` (nenhum teste toca a API real).
- Checkstyle 11.0.1 com configuração do Google Java Style.
- Spotless com `google-java-format` para formatação automática.
- `Makefile` com alvos `lint`, `format`, `test`, `verify` e `clean`.
- Hook `pre-commit` versionado em `.githooks/` que executa `make lint`.
- `setup.sh` para ativar o `hooksPath` do git após clone.

## [0.1.0] - 2026-07-20

### Adicionado
- Inicialização do projeto.
