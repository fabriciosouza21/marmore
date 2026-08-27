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
cp .env.example .env       # e preencha as credenciais
source ./load-env.sh       # carrega variáveis no shell atual (repita a cada sessão)
```

## Infraestrutura local

A infra de containers (Postgres e MinIO) é gerenciada pelo **Portainer**
([https://localhost:9443](https://localhost:9443)), a partir da pasta `deploy/`
do workspace: stack `banco` (Postgres compartilhado entre aplicações, banco
`marmore`) e stack `marmore` (MinIO das imagens). As credenciais estão no
`.env` (template em `.env.example`). Detalhes e script de deploy em
`deploy/README.md`.

## Configuração

A chave `OPENAI_API_KEY` deve ser exportada no ambiente (ou via `.env`
carregado por `load-env.sh`). Sem ela, `POST /images/edit` responde `503`
com a mensagem `OPENAI_API_KEY ausente`.

O endpoint de edição precisa ainda de uma imagem de referência da pedra
(`granito.png`) enviada como segundo anexo do multipart. O caminho padrão é
`${user.dir}/data/granito.png` e o arquivo `data/granito.png` já vem do
repositório, então funciona após um clone limpo. Para trocar a pedra,
sobrescreva `marmore.openai.image.stone-path` (em `application.yaml`, por
variável de ambiente `MARMORE_OPENAI_IMAGE_STONE_PATH` ou argumento
`--marmore.openai.image.stone-path=...`):

```yaml
marmore:
  openai:
    image:
      stone-path: ${user.dir}/data/granito.png
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
