# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
e este projeto adere ao [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [Não publicado]

### Adicionado
- Checkstyle 11.0.1 com configuração do Google Java Style.
- Spotless com `google-java-format` para formatação automática.
- `Makefile` com alvos `lint`, `format`, `test`, `verify` e `clean`.
- Hook `pre-commit` versionado em `.githooks/` que executa `make lint`.
- `setup.sh` para ativar o `hooksPath` do git após clone.

## [0.1.0] - 2026-07-20

### Adicionado
- Inicialização do projeto.
