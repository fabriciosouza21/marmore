# Convenções

## Commits

Seguir Conventional Commits.

## Versionamento

Seguir Semantic Versioning. Tags no formato `X.Y.Z` mantidas em sincronia com `<version>` do `pom.xml`.

## Healthcheck e versao

`GET /health` e publico (sem `X-API-Key`) e responde `{"status":"ok","version":"<versao>"}`.
A versao vem do `<version>` do `pom.xml` via goal `build-info` do spring-boot-maven-plugin
(bean `BuildProperties`); nao existe versao hardcoded em codigo. O healthcheck do container
na stack de deploy usa esse endpoint, e a versao no ar se consulta com:

```bash
curl https://api.marmoraria.fsmdevs.com/health
```

Bumpar a versao = alterar `<version>` do pom (e tag correspondente); o `/health` acompanha
no proximo build/deploy da imagem.

## Branches

Seguir GitFlow.

- `main`: produção. Recebe apenas merges de `release/` e `hotfix/`. Tags de release são criadas aqui.
- `develop`: integração contínua. Todas as features convergem aqui.
- `feature/<nome>`: nova funcionalidade. Origem e destino: `develop`.
- `bugfix/<nome>`: correção de bug. Origem e destino: `develop`.
- `release/<X.Y.Z>`: preparação de versão. Origem: `develop`. Destino: `main` e `develop`.
- `hotfix/<X.Y.Z>`: correção urgente em produção. Origem: `main`. Destino: `main` e `develop`.

Nomes de branch em kebab-case, sem acentos. Verbo no infinitivo descrevendo a entrega.

### Fluxo resumido

```bash
# Nova feature
git checkout develop
git checkout -b feature/gerar-imagem-pia-americana

# Ao concluir: merge de volta para develop
git checkout develop
git merge --no-ff feature/gerar-imagem-pia-americana
git branch -d feature/gerar-imagem-pia-americana
```

## Estilo de codigo

Seguir [Google Java Style](https://google.github.io/styleguide/javaguide.html).

- Checkstyle 11.0.1 com `config/checkstyle/checkstyle.xml` (copia do `google_checks.xml` oficial).
- Indentacao 2 espacos, sem tabs, line length 100.
- Spotless com `google-java-format` corrige formatacao automaticamente.
- Comandos: `make lint` verifica, `make format` corrige.

