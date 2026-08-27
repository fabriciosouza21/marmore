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
cp .env.example .env       # e preencha OPENAI_API_KEY
source ./load-env.sh       # carrega variáveis no shell atual (repita a cada sessão)
```

## Infraestrutura local

O `docker compose` na raiz sobe Postgres e MinIO com um comando:

```bash
docker compose up -d
```

| Serviço | Porta | Uso |
|---------|-------|-----|
| Postgres | 5432 | metadados das gerações (tabela `generated_image`) |
| MinIO S3 | 9000 (API), 9001 (console) | imagens `.png` no bucket `marmore-imagens-geradas` |

As credenciais vêm do `.env` (template em `.env.example`); os defaults de dev
funcionam sem configuração extra. O bucket é criado automaticamente pelo
serviço `minio-init`. Os dados persistem em volumes: `docker compose down`
mantém, `down -v` apaga.

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
