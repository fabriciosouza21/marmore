# marmore-api

API do projeto marmore, construída com Spring Boot 4.1 e Java 26.

## Pré-requisitos

- [SDKMAN](https://sdkman.io/) com Java 26 ativo. Confira com `java -version`.
- Maven é executado via `./mvnw`, dispensando instalação global.

## Setup após clone

```bash
git clone git@github.com:fabriciosouza21/marmore.git
cd marmore/api
./setup.sh                 # ativa o hook pre-commit
cp .env.example .env       # se houver exemplo; senão crie à mão
source ./load-env.sh       # carrega variáveis no shell atual (repita a cada sessão)
```

## Comandos

Rode `make help` para a lista completa. Os principais:

| Comando          | Ação                                              |
|------------------|---------------------------------------------------|
| `make lint`      | Verifica Checkstyle e Spotless sem corrigir.      |
| `make format`    | Corrige a formatação automaticamente.             |
| `make test`      | Executa os testes.                                |
| `make verify`    | Pipeline completo: lint, testes e empacotamento.  |
| `make clean`     | Remove `target/`.                                 |

O hook `pre-commit` roda `make lint` antes de cada commit. Para bypass em
emergências use `SKIP_PRE_COMMIT=1 git commit ...`.

## Convenções

- Commits seguem [Conventional Commits](https://www.conventionalcommits.org/).
- Versionamento segue [Semantic Versioning](https://semver.org/lang/pt-BR/), com tags no formato `X.Y.Z` sincronizadas com a `<version>` do `pom.xml`.
- Mudanças notáveis são registradas no [`CHANGELOG.md`](./CHANGELOG.md).
- Estilo de código segue o [Google Java Style](https://google.github.io/styleguide/javaguide.html) (2 espaços, sem tabs).
