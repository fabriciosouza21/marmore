# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
e este projeto adere ao [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [Não publicado]

### Adicionado
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
