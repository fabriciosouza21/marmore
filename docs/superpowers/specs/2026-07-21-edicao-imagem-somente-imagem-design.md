# Design: endpoint de edição recebe apenas a imagem

**Data:** 2026-07-21
**Branch:** `feature/endpoint-edicao-imagem`
**Status:** draft

## Contexto

O endpoint `POST /images/edit` hoje repassa ao `/v1/images/edits` da OpenAI o
`prompt` e duas imagens (`ambiente` + `granito`) enviadas pelo cliente. O
cliente, no entanto, só deveria se preocupar com a foto do ambiente a ser
editada. O `prompt` e a imagem da pedra são insumos fixos do produto, não
dados que o cliente escolhe.

O Python de referência (`openai_image/openai_image.py`) é genérico: recebe
`prompt` e `image_paths` como parâmetros. O texto do prompt vive em
`openai_image/prompt.md` e a imagem da pedra em `openai_image/assets/granito.png`.
Esta spec formaliza o port desses dois insumos para o backend Java, deixando
o contrato HTTP só com a imagem do ambiente.

Segundo problema: uploads acima de ~5 MB quebram no Spring (`MaxUploadSizeExceeded`),
limite default de 1 MB por arquivo. Fotos reais de celular estouram esse limite.

## Objetivo

Contrato do endpoint passa a ser:

```
POST /images/edit   multipart/form-data
  image: <arquivo único — ambiente a ser editado>

→ 200 image/png | 400 | 413 | 502 | 503
```

O `prompt` some do request. A lista `images` vira `image` singular. Cliente
manda apenas a foto do ambiente.

## Decisões

| Decisão | Escolha | Razão |
|---|---|---|
| Origem do prompt | Constante Java | Decisão do cliente. Texto fixo, muda raramente. |
| Origem da pedra | Path configurável no `application.yaml` | Decisão do cliente. Trocar pedra não exige rebuild. |
| Texto do prompt | Igual ao `prompt.md` do Python | Decisão do cliente. Fonte canônica. |
| Campo multipart | `image` (singular) | Decisão do cliente. Reflete novo contrato. |
| Pedra ausente em disco | 503 Service Unavailable | Decisão do cliente. Falha de config do serviço. |
| Biblioteca de resize | Thumbnailator | Reduz risco, API trivial, ~100KB. |
| `max-file-size` do Spring | 25 MB | Coincide com o limite da OpenAI. |

## Componentes

### `EditPrompts` (novo)

`src/main/java/com/marmore/api/image/domain/EditPrompts.java`

Classe de domínio com constante `static final String COUNTERTOP` usando
Java text block. Conteúdo idêntico a `openai_image/prompt.md`.

### `ImageResizer` (novo)

`src/main/java/com/marmore/api/image/service/ImageResizer.java`

Recebe `byte[]` e devolve `byte[]` redimensionado e re-codificado. Regras:

- Maior lado reduzido para no máximo `1536` mantendo aspecto.
- Não faz upscaling (imagens menores passam ilesas em dimensão).
- Re-codifica sempre como JPEG qualidade `0.85` em memória.
- Nunca lança: entrada inválida vira `Optional.empty()`, deixando o
  chamador decidir como tratar.

Implementação sobre `net.coobird:thumbnailator`.

### `ImageEditProperties` (alterado)

Adiciona campo `stonePath` do tipo `java.nio.file.Path`, com binding
automático do Spring. Default aponta para `${user.dir}/data/granito.png`.

```yaml
marmore:
  openai:
    image:
      stone-path: ${user.dir}/data/granito.png
```

### `ImageEditController` (alterado)

Assinatura muda:

```java
@PostMapping(value = "/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<byte[]> edit(@RequestParam("image") MultipartFile image)
    throws IOException
```

Não há mais `prompt`. O controller só lê o `byte[]` da imagem do cliente e
repassa ao service. Não toca em disco para ler a pedra (isso fica no service).

### `ImageEditService` (alterado)

Fluxo de `generate(byte[] ambienteBytes)`:

1. Valida api-key (igual hoje). Vazio → `Err("OPENAI_API_KEY ausente...")`.
2. Carrega pedra: `new FileSystemResource(props.getStonePath())`. Se
   `!resource.exists()` → `Err("stone image not found: <path>")`. Controller
   mapeia para 503.
3. Passa `ambienteBytes` pelo `ImageResizer`. Se voltar vazio →
   `Err("unable to decode input image")`. Controller mapeia para 400.
4. Monta multipart na ordem **`[ambiente, pedra]`**, porque o prompt
   referencia `IMAGE 1` (ambiente) e `IMAGE 2` (granito). A ordem é
   semântica.
5. Usa prompt fixo de `EditPrompts.COUNTERTOP`.
6. Chama `/v1/images/edits` e trata resposta como hoje (`data[0].b64_json`).

Assinatura deixa de receber `prompt` e `List<Resource>` e passa a receber
`byte[] ambienteBytes`. `EditOptions` continua existindo (defaults
internos). A pedra deixa de vir por parâmetro; vem do `props`.

### `application.yaml` (alterado)

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 25MB
      max-request-size: 25MB

marmore:
  openai:
    image:
      stone-path: ${user.dir}/data/granito.png
```

### `pom.xml` (alterado)

```xml
<dependency>
  <groupId>net.coobird</groupId>
  <artifactId>thumbnailator</artifactId>
  <version>0.4.21</version>
</dependency>
```

> Versão 0.4.21 lançada em Out/2025. Disponível no Maven Central.

### Bruno `editar_imagem.bru` (alterado)

Remove `prompt` e a segunda `images`. Fica só:

```
body:multipart-form {
  image: @file(./assets/ambiente.jpg)
}
```

## Mapeamento de status HTTP

| Condição | Status | Origem |
|---|---|---|
| api-key ausente | 503 | Service |
| pedra não encontrada em disco | 503 | Service |
| imagem do cliente indecodificável | 400 | Service |
| upload excede 25 MB | 413 | Spring (automático) |
| erro HTTP da OpenAI | 502 | Service |
| demais erros | 502 | Service |

## Contrato multipart (para evitar ambiguidade)

Existem dois muiltiparts distintos nesta integração:

### 1. Cliente → Backend (`POST /images/edit`)

Campo singular, nome `image`:

```
Content-Disposition: form-data; name="image"; filename="ambiente.jpg"
```

### 2. Backend → OpenAI (`POST /v1/images/edits`)

Mantém o comportamento atual do service: campo `image[]` repetido, na
ordem **`[ambiente, pedra]`**. A OpenAI espera múltiplas entradas sob o
mesmo nome; a ordem define quem é `IMAGE 1` (ambiente) e `IMAGE 2`
(pedra), conforme o prompt fixo referencia.

```
image[]: <ambiente redimensionado>
image[]: <pedra do stonePath>
```

O `prompt` enviado é o da constante `EditPrompts.COUNTERTOP`.

## Responsabilidade de extração do `byte[]`

- O **controller** extrai `image.getBytes()` do `MultipartFile`. O service
  não conhece `MultipartFile` (continua web-agnostic).
- O service recebe `byte[]` e devolve `GenerateResult`.

## Fluxo

```
Cliente
  │ POST /images/edit  multipart: image=<ambiente>
  ▼
ImageEditController.edit(MultipartFile image)
  │ byte[] ambiente = image.getBytes()
  ▼
ImageEditService.generate(byte[] ambiente)
  │
  ├─ 1. apiKey ausente? ───────────► Err("OPENAI_API_KEY ausente")  → 503
  │
  ├─ 2. FileSystemResource(stonePath).exists() == false?
  │      └────────────────────────► Err("stone image not found")    → 503
  │
  ├─ 3. ImageResizer.resize(ambiente) == empty?
  │      └────────────────────────► Err("unable to decode input")   → 400
  │
  ├─ 4. multipart body: image[] = [ambiente, pedra] + prompt fixo
  │
  ├─ 5. POST /v1/images/edits  ─────► OpenAI
  │
  └─ 6. data[0].b64_json ───────────► Ok(b64)                        → 200 PNG
                                     └─ Erro HTTP → Err(...)         → 502
```

## Testes

### `ImageResizerTest` (novo)

- Imagem > 1536px: maior lado reduzido para 1536.
- Imagem ≤ 1536px: dimensões preservadas.
- `byte[]` inválido (não é imagem): devolve `Optional.empty()`, não lança.

### `ImageEditServiceTest` (alterado)

- Manter casos existentes ajustados para a nova assinatura (`generate(byte[])`
  em vez de `generate(prompt, List<Resource>, opts)`).
- Novo caso: `stonePath` apontando para arquivo inexistente → `Err("stone
  image not found...")` sem chamar a API (`server.verify()`).
- Novo caso: `ambiente` inválido → `Err("unable to decode input image")`
  sem chamar a API.

### `ImageEditControllerTest` (alterado)

- Sucesso: manda `image` singular, sem `param("prompt", ...)`, espera 200 PNG.
- Api-key vazia: 503.
- Pedra ausente (path inválido): 503.
- Erro HTTP da OpenAI: 502.

### Demais testes

- `EditOptionsTest` — sem alteração de comportamento.
- `GenerateResultTest` — sem alteração.
- `ImageEditPropertiesTest` — adicionar asserção de binding de `stone-path`.
- `RestClientConfigTest` — sem alteração.
- `FileSystemResultWriterTest` — sem alteração.

## Fora de escopo (YAGNI)

- Cache em memória da pedra (ler do disco por request é barato).
- Suporte a múltiplas pedras ou troca dinâmica.
- Preservação de transparência alpha (cliente manda JPG; se mandar PNG, é
  re-codificado como JPEG e perde alpha — aceitável para foto de ambiente).
- Validação de content-type do upload.
- `StoneImageProvider` dedicado — extração prematura.

## Riscos

- **Ordem do multipart.** O prompt referencia `IMAGE 1`/`IMAGE 2`. Se a
  OpenAI receber na ordem inversa, o resultado perde a semântica. Mitigação:
  teste que verifica a ordem via `MockRestServiceServer`.
- **Quality do resize.** Thumbnailator com bilinear + JPEG 0.85 é
  empiricamente bom para foto, mas pode perder nitidez em texturas de pedra.
  Se a OpenAI devolver material desbotado, subir `outputQuality` para 0.9.
- **Prompt fixo acoplado ao código.** Mudar o prompt exige rebuild. Decisão
  consciente do cliente.
