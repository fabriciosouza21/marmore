# Documentação de Arquitetura — C4 Model

Modelo C4 da Marmore API, organizado por níveis. Cada nível dá um zoom
no anterior: do contexto do sistema até os componentes internos, e por fim
fluxos dinâmicos que mostram o comportamento em execução.

A biblioteca C4-PlantUML está versionada localmente em `lib/` (sem dependência
de internet para renderizar).

## Estrutura

```
c4-model/
├── README.md                         # este índice
├── lib/                              # C4-PlantUML stdlib + sprites (offline)
│   ├── C4_*.puml                     # macros C4 (Context/Container/Component/Dynamic)
│   └── icons/sprites/                # sprites de ícones (apenas a definição sprite $xxx {...})
├── 1-context/                        # Nível 1 — Contexto do Sistema
│   └── system-context.puml
├── 2-container/                      # Nível 2 — Containers
│   └── containers.puml
├── 3-component/                      # Nível 3 — Componentes
│   └── components.puml
└── dynamic/                          # Diagramas dinâmicos (fluxos)
    ├── edicao-imagem-sequence.puml   # fluxo síncrono atual (/images/edit)
    ├── sse-user-flow.puml            # fluxo SSE — visão do usuário
    └── internal-flow.puml            # fluxo SSE — arquitetura de camadas
```

## Ícones

Os três diagramas estáticos (Níveis 1, 2 e 3) usam sprites gráficos nos
elementos para reforçar visualmente o papel de cada um. Os sprites vêm da
coleção [tupadr3/plantuml-icon-font-sprites][tupadr3] (Font Awesome 5 +
Devicons), mas versionados localmente em `lib/icons/sprites/`.

Importante: só a definição `sprite $nome [...] { ... }` é incluída, sem as
macros auxiliares (`!define`, `ENTITY`, `skinparam folderBackgroundColor`)
que conflitam com o C4-PlantUML. Por isso os arquivos em
`lib/icons/sprites/` contêm apenas o bloco `sprite`.

Mapeamento de ícones por elemento:

| Elemento                       | Ícone              | Sprite (`$sprite=`)   |
|--------------------------------|--------------------|-----------------------|
| Cliente / Operador             | pessoas            | — (default Person)    |
| Marmore API (Sistema)          | servidor           | `server`              |
| OpenAI API                     | robô               | `robot`               |
| Frontend / Bruno               | desktop            | `desktop`             |
| API Spring Boot                | logo Spring        | `spring_original`     |
| Data Folder                    | arquivo            | `file`                |
| Serviço de Cotação             | cifrão             | `dollar_sign`         |
| ImageEditController / Service  | engrenagem         | `cog`                 |
| ImageResizer                   | imagem             | `image`               |
| OpenAiRestClientImageEditModel | ramificação código | `code_branch`         |
| ApiKeyAuthFilter (Security)    | escudo             | `shield_alt`          |

Os diagramas dinâmicos (sequência) não recebem sprites, pois usam
sintaxe PlantUML clássica, não as macros C4.

[tupadr3]: https://github.com/tupadr3/plantuml-icon-font-sprites

## Níveis

### Nível 1 — Contexto
`1-context/system-context.puml`
Visão de alto nível: quem usa o sistema, com o que ele se integra.
Pessoas (Cliente, Operador), o sistema Marmore API e o sistema externo
OpenAI. Mostra a dependência da imagem da pedra em disco.

### Nível 2 — Containers
`2-container/containers.puml`
Unidades executáveis/deployáveis dentro do sistema Marmore API:
Frontend/Bruno (cliente HTTP), API Spring Boot (backend) e o Data Folder
(granito.png em disco). Sistemas externos: OpenAI e serviço de cotação
USD/BRL (com cache de 1x ao dia).

### Nível 3 — Componentes
`3-component/components.puml`
Zoom no container "API Spring Boot": seus componentes internos
(Controller, Service, Resizer, ImageEditModel, gateway OpenAI e filtro
de segurança) e as relações entre eles. Deixa explícita a separação de
camadas web/service/ai e o contrato de erros (gateway lança, service
traduz em GenerateResult, controller nunca recebe exceção).

### Diagramas dinâmicos
`dynamic/`
Comportamento em execução, por meio de sequências de mensagens.

- **`edicao-imagem-sequence.puml`** — caminho feliz do endpoint síncrono
  atual `POST /images/edit` (retorna PNG 200).
- **`sse-user-flow.puml`** — fluxo do novo endpoint SSE
  `/images/edit/stream`, na perspectiva do usuário. Foco em clareza
  sobre a natureza do SSE (canal unidirecional, eventos de progresso,
  heartbeat durante a espera da OpenAI).
- **`internal-flow.puml`** — mesmo fluxo SSE, mas na perspectiva interna
  das camadas. Numera cada passo e detalha os contratos entre Controller,
  Service, Resizer e gateway.

## Como renderizar

Requer Java e o jar do PlantUML:

```bash
java -jar plantuml.jar -tpng caminho/para/arquivo.puml
```

Ou renderizar tudo de uma vez:

```bash
java -jar plantuml.jar -tpng \
  1-context/system-context.puml \
  2-container/containers.puml \
  3-component/components.puml \
  dynamic/edicao-imagem-sequence.puml \
  dynamic/sse-user-flow.puml \
  dynamic/internal-flow.puml
```

Os `.png` são gerados ao lado de cada `.puml`.

## Decisões de arquitetura (resumo)

- **Endpoint SSE novo** (`/images/edit/stream`) mantém o endpoint
  síncrono `/images/edit` intacto.
- **Evento `event:` nativo do SSE** para discriminar tipos
  (`status`, `imagem`, `done`, `error`, `ping`).
- **`timestamp`** em cada evento; **`latency_ms`** apenas no `done`.
- **Heartbeat** (`event: ping`) a cada 15s durante a chamada bloqueante
  da OpenAI (até 180s), porque a OpenAI não faz streaming da geração.
- **Evento final de imagem**: base64 puro no `data:`, sem wrapper JSON.
- **Erros viram eventos** no stream (`event: error`), não exceções HTTP,
  porque em SSE o status 200 já foi enviado antes do erro ocorrer.
