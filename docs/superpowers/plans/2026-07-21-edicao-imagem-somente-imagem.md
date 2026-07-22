# Edição de Imagem — Apenas Imagem (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mudar o contrato de `POST /images/edit` para receber apenas a imagem do ambiente, com o backend injetando o prompt fixo e a imagem da pedra, e resolver o limite de 25 MB de upload com redimensionamento via Thumbnailator.

**Architecture:** Controller extrai `byte[]` do `MultipartFile` singular (`image`). Service orquestra: valida api-key, carrega pedra do `stone-path` configurado, redimensiona a imagem do cliente para no máximo 1536 px, monta multipart na ordem `[ambiente, pedra]` com prompt fixo, e chama a OpenAI. Erros viram `GenerateResult.Err`, nunca lançam do service.

**Tech Stack:** Java 26, Spring Boot 4.1, RestClient, Thumbnailator 0.4.21, JUnit 5, AssertJ, MockRestServiceServer, Google Java Style (Checkstyle + Spotless).

## Global Constraints

- Indentação 2 espaços, sem tabs, line length 100. `make format` corrige, `make lint` verifica.
- Commits seguem Conventional Commits. Um commit por task (ou por passo conforme indicado).
- Branch atual: `feature/endpoint-edicao-imagem`. Não abrir PR, não mergear em `develop` neste plano.
- Tipos de erro: nunca lançar do service. Erros viram `GenerateResult.Err`.
- Versão do thumbnailator: `0.4.20` é estável, mas o plano usa `0.4.21` (Maven Central, Out/2025).
- Idioma dos Javadocs: português sem acentuação na fonte (manter padrão dos arquivos existentes).

---

## File Structure

**Arquivos novos:**
- `src/main/java/com/marmore/api/image/domain/EditPrompts.java` — constante do prompt fixo (text block de `prompt.md`).
- `src/main/java/com/marmore/api/image/service/ImageResizer.java` — resize/compressão via Thumbnailator.
- `src/test/java/com/marmore/api/image/domain/EditPromptsTest.java` — asserção de conteúdo do prompt.
- `src/test/java/com/marmore/api/image/service/ImageResizerTest.java` — resize e tratamento de entrada inválida.

**Arquivos alterados:**
- `pom.xml` — adicionar thumbnailator.
- `src/main/resources/application.yaml` — `spring.servlet.multipart` + `stone-path`.
- `src/main/java/com/marmore/api/image/config/ImageEditProperties.java` — adicionar `stonePath`.
- `src/main/java/com/marmore/api/image/web/ImageEditController.java` — assinatura sem `prompt`, `image` singular.
- `src/main/java/com/marmore/api/image/service/ImageEditService.java` — orquestra prompt fixo + pedra + resize.
- `src/test/java/com/marmore/api/image/config/ImageEditPropertiesTest.java` — asserção de binding de `stonePath`.
- `src/test/java/com/marmore/api/image/service/ImageEditServiceTest.java` — ajustar assinatura + casos novos.
- `src/test/java/com/marmore/api/image/web/ImageEditControllerTest.java` — ajustar para `image` singular + casos novos.
- `bruno/marmore-api/editar_imagem.bru` — remover `prompt` e segunda `images`.

---

## Task 1: Adicionar dependência Thumbnailator ao `pom.xml`

**Files:**
- Modify: `pom.xml` (dentro de `<dependencies>`)

**Interfaces:**
- Produces: `net.coobird:thumbnailator:0.4.21` disponível no classpath.

- [ ] **Step 1: Adicionar a dependência**

Inserir logo após o bloco do `spring-boot-starter-webmvc` (linhas próximas a 45-47 do `pom.xml`):

```xml
<dependency>
  <groupId>net.coobird</groupId>
  <artifactId>thumbnailator</artifactId>
  <version>0.4.21</version>
</dependency>
```

- [ ] **Step 2: Verificar que o JAR resolve**

Run: `./mvnw dependency:resolve -DincludeArtifactIds=thumbnailator -q`
Expected: sucesso, sem "Could not resolve". (Pode exibir "No artifacts to satisfy goal" porque o filtro é restritivo; rodar `./mvnw compile -q` como fallback.)

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore(image): adiciona thumbnailator para redimensionamento"
```

---

## Task 2: `EditPrompts` — constante do prompt fixo

**Files:**
- Create: `src/main/java/com/marmore/api/image/domain/EditPrompts.java`
- Test: `src/test/java/com/marmore/api/image/domain/EditPromptsTest.java`

**Interfaces:**
- Produces: `EditPrompts.COUNTERTOP` (campo `static final String`) com o texto exato do prompt de bancada.

- [ ] **Step 1: Escrever o teste falhando**

Criar `src/test/java/com/marmore/api/image/domain/EditPromptsTest.java`:

```java
package com.marmore.api.image.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Testes de {@link EditPrompts}. */
class EditPromptsTest {

  @Test
  void countertopContemMarcadoresDeImagem1EImagem2() {
    String prompt = EditPrompts.COUNTERTOP;

    assertThat(prompt).contains("IMAGE 1");
    assertThat(prompt).contains("IMAGE 2");
    assertThat(prompt).contains("drainboard");
    assertThat(prompt).startsWith("I am sending two images.");
  }
}
```

- [ ] **Step 2: Rodar o teste para confirmar falha**

Run: `./mvnw test -Dtest=EditPromptsTest -q`
Expected: erro de compilação (`EditPrompts` não existe).

- [ ] **Step 3: Implementar `EditPrompts`**

Criar `src/main/java/com/marmore/api/image/domain/EditPrompts.java`. O texto do text block deve copiar **exatamente** o conteúdo de `/home/fsm/Documentos/mamoraria/openai_image/prompt.md`. Para garantir byte-a-byte, copiar do arquivo com:

```bash
cat /home/fsm/Documentos/mamoraria/openai_image/prompt.md
```

E montar a classe assim (colar o conteúdo do `prompt.md` dentro do text block, preservando quebras de linha e sem indentation extra além dos 2 espaços do Google Style):

```java
package com.marmore.api.image.domain;

/**
 * Prompts fixos de edicao. Textos que o produto emprega sem entrada do cliente.
 */
public final class EditPrompts {

  /**
   * Prompt de bancada suspensa em granito com cuba embutida e escorredor rebaixado. Referencia
   * IMAGE 1 (ambiente) e IMAGE 2 (granito). Ordem do multipart no service deve respeitar essa
   * convencão.
   */
  public static final String COUNTERTOP = """
      <COLAR AQUI O CONTEÚDO DE prompt.md, COM INDENTACAO DE 2 ESPACOS>
      """;

  private EditPrompts() {}
}
```

- [ ] **Step 4: Rodar o teste para confirmar passagem**

Run: `./mvnw test -Dtest=EditPromptsTest -q`
Expected: PASS.

- [ ] **Step 5: Formatar**

Run: `make format`
Expected: `Spotless keeping N files clean`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/marmore/api/image/domain/EditPrompts.java \
        src/test/java/com/marmore/api/image/domain/EditPromptsTest.java
git commit -m "feat(image): adiciona prompt fixo de bancada como constante"
```

---

## Task 3: `ImageResizer` — resize/compressão via Thumbnailator

**Files:**
- Create: `src/main/java/com/marmore/api/image/service/ImageResizer.java`
- Test: `src/test/java/com/marmore/api/image/service/ImageResizerTest.java`

**Interfaces:**
- Produces: `ImageResizer.resize(byte[] input) -> java.util.Optional<byte[]>`.
  - Sempre JPEG q=0.85 na saída.
  - Reduz maior lado para no máximo 1536 mantendo aspecto.
  - Não faz upscaling.
  - Entrada inválida → `Optional.empty()` (não lança).

- [ ] **Step 1: Escrever os testes falhando**

Criar `src/test/java/com/marmore/api/image/service/ImageResizerTest.java`. Para gerar uma imagem real em memória no teste, usar `BufferedImage` + `ImageIO` (JDK):

```java
package com.marmore.api.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Testes de {@link ImageResizer}. */
class ImageResizerTest {

  private final ImageResizer resizer = new ImageResizer();

  @Test
  void reduzMaiorLadoPara1536QuandoEntradaMaior() throws Exception {
    byte[] entrada = gerarPngRbg(2000, 1000);

    Optional<byte[]> saidaOpt = resizer.resize(entrada);

    assertThat(saidaOpt).isPresent();
    BufferedImage saida = ImageIO.read(new ByteArrayInputStream(saidaOpt.get()));
    assertThat(Math.max(saida.getWidth(), saida.getHeight())).isLessThanOrEqualTo(1536);
    assertThat(saida.getWidth()).isGreaterThan(saida.getHeight());
  }

  @Test
  void naoFazUpscaleQuandoEntradaMenorQue1536() throws Exception {
    byte[] entrada = gerarPngRbg(800, 600);

    Optional<byte[]> saidaOpt = resizer.resize(entrada);

    assertThat(saidaOpt).isPresent();
    BufferedImage saida = ImageIO.read(new ByteArrayInputStream(saidaOpt.get()));
    assertThat(saida.getWidth()).isEqualTo(800);
    assertThat(saida.getHeight()).isEqualTo(600);
  }

  @Test
  void comprimeSempreMesmoQuandoNaoRedimensiona() throws Exception {
    // PNG sem compressão JPEG: saida JPEG 0.85 deve ser menor que a entrada.
    byte[] entrada = gerarPngRbg(1000, 1000);

    Optional<byte[]> saidaOpt = resizer.resize(entrada);

    assertThat(saidaOpt).isPresent();
    assertThat(saidaOpt.get().length).isLessThan(entrada.length);
  }

  @Test
  void entradaInvalidaDevolveEmptySemLancar() {
    Optional<byte[]> saidaOpt = resizer.resize(new byte[] {1, 2, 3, 4});

    assertThat(saidaOpt).isEmpty();
  }

  /** Gera um PNG RGB puro com dimensões dadas. */
  private static byte[] gerarPngRbg(int w, int h) throws Exception {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(java.awt.Color.GREEN);
    g.fillRect(0, 0, w, h);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "png", out);
    return out.toByteArray();
  }
}
```

- [ ] **Step 2: Rodar os testes para confirmar falha**

Run: `./mvnw test -Dtest=ImageResizerTest -q`
Expected: erro de compilação (`ImageResizer` não existe).

- [ ] **Step 3: Implementar `ImageResizer`**

Criar `src/main/java/com/marmore/api/image/service/ImageResizer.java`:

```java
package com.marmore.api.image.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

/**
 * Redimensiona e re-codifica imagens em memoria. Entrada invalida vira {@link Optional#empty()},
 * nunca lanca. Saida e sempre JPEG qualidade 0.85.
 */
@Component
public class ImageResizer {

  private static final int MAX_LADO = 1536;
  private static final double QUALIDADE = 0.85;

  /**
   * Redimensiona a imagem de entrada para no maximo {@value MAX_LADO} no maior lado, mantendo
   * aspecto. Nao faz upscaling. Re-codifica como JPEG qualidade {@value QUALIDADE}.
   *
   * @param input bytes da imagem original (PNG, JPEG, etc.)
   * @return imagem redimensionada, ou empty se a entrada nao for decodificavel
   */
  public Optional<byte[]> resize(byte[] input) {
    if (input == null || input.length == 0) {
      return Optional.empty();
    }
    BufferedImage original;
    try {
      original = ImageIO.read(new ByteArrayInputStream(input));
    } catch (IOException e) {
      return Optional.empty();
    }
    if (original == null) {
      return Optional.empty();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      Thumbnails.of(original)
          .size(MAX_LADO, MAX_LADO)
          .outputFormat("jpg")
          .outputQuality(QUALIDADE)
          .toOutputStream(out);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Optional.of(out.toByteArray());
  }
}
```

- [ ] **Step 4: Rodar os testes para confirmar passagem**

Run: `./mvnw test -Dtest=ImageResizerTest -q`
Expected: PASS (4 testes).

- [ ] **Step 5: Formatar e verificar lint**

Run: `make format && make lint`
Expected: 0 violações Checkstyle, Spotless clean.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/marmore/api/image/service/ImageResizer.java \
        src/test/java/com/marmore/api/image/service/ImageResizerTest.java
git commit -m "feat(image): adiciona ImageResizer com Thumbnailator"
```

---

## Task 4: Adicionar `stonePath` em `ImageEditProperties`

**Files:**
- Modify: `src/main/java/com/marmore/api/image/config/ImageEditProperties.java`
- Test: `src/test/java/com/marmore/api/image/config/ImageEditPropertiesTest.java`

**Interfaces:**
- Produces: `ImageEditProperties.getStonePath() -> java.nio.file.Path`.

- [ ] **Step 1: Atualizar o teste falhando**

Em `src/test/java/com/marmore/api/image/config/ImageEditPropertiesTest.java`, adicionar a propriedade `marmore.openai.image.stone-path=/tmp/pedra.png` na anotação `@SpringBootTest(properties = {...})` e adicionar uma asserção no método `bindResolvePropriedades`:

Trocar o bloco `@SpringBootTest(...)` existente por:

```java
@SpringBootTest(
    properties = {
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-teste",
      "marmore.openai.image.default-model=gpt-image-1.5",
      "marmore.openai.image.timeout=30s",
      "marmore.openai.image.stone-path=/tmp/pedra.png"
    })
```

Adicionar dentro do método `bindResolvePropriedades`, após a asserção de `timeout`:

```java
assertThat(props.getStonePath()).isEqualTo(java.nio.file.Paths.get("/tmp/pedra.png"));
```

- [ ] **Step 2: Rodar o teste para confirmar falha**

Run: `./mvnw test -Dtest=ImageEditPropertiesTest -q`
Expected: falha de compilação (`getStonePath()` não existe).

- [ ] **Step 3: Adicionar o campo em `ImageEditProperties`**

Em `src/main/java/com/marmore/api/image/config/ImageEditProperties.java`:

Adicionar import:
```java
import java.nio.file.Path;
```

Adicionar campo após `timeout`:
```java
private Path stonePath;
```

Adicionar getter/setter ao final da classe (antes do `}`):
```java
/** Retorna o caminho do arquivo da pedra (granito) enviado como IMAGE 2. */
public Path getStonePath() {
  return stonePath;
}

public void setStonePath(Path stonePath) {
  this.stonePath = stonePath;
}
```

- [ ] **Step 4: Rodar o teste para confirmar passagem**

Run: `./mvnw test -Dtest=ImageEditPropertiesTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marmore/api/image/config/ImageEditProperties.java \
        src/test/java/com/marmore/api/image/config/ImageEditPropertiesTest.java
git commit -m "feat(image): adiciona stonePath em ImageEditProperties"
```

---

## Task 5: Atualizar `application.yaml` com multipart e `stone-path`

**Files:**
- Modify: `src/main/resources/application.yaml`

**Interfaces:**
- Produz config efetiva em runtime. Não tem teste direto (config de produção).

- [ ] **Step 1: Atualizar o yaml**

O conteúdo final de `src/main/resources/application.yaml` deve ficar:

```yaml
spring:
  application:
    name: api
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
  http:
    client:
      read-timeout: 180s
  servlet:
    multipart:
      max-file-size: 25MB
      max-request-size: 25MB

marmore:
  openai:
    image:
      base-url: https://api.openai.com
      api-key: ${spring.ai.openai.api-key:}
      default-model: gpt-image-2
      timeout: 180s
      stone-path: ${user.dir}/data/granito.png
```

- [ ] **Step 2: Validar via `ApiApplicationTests`**

Run: `./mvnw test -Dtest=ApiApplicationTests -q`
Expected: PASS. Se quebrar por `stone-path` inexistente em disco, está OK — `ApiApplicationTests` só sobe o contexto.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.yaml
git commit -m "chore(image): ajusta limites multipart e adiciona stone-path"
```

---

## Task 6: Refatorar `ImageEditService` para prompt fixo + pedra + resize

**Files:**
- Modify: `src/main/java/com/marmore/api/image/service/ImageEditService.java`
- Test: `src/test/java/com/marmore/api/image/service/ImageEditServiceTest.java`

**Interfaces:**
- Consumes:
  - `ImageEditProperties.getStonePath() -> Path` (Task 4)
  - `ImageResizer.resize(byte[]) -> Optional<byte[]>` (Task 3)
  - `EditPrompts.COUNTERTOP` (Task 2)
- Produces: `ImageEditService.generate(byte[] ambiente) -> GenerateResult`.

> Esta task é grande porque muda a assinatura central. Vamos por etapas: primeiro ajustar os testes existentes (quebrar compilação é esperado), depois refatorar o service, depois adicionar casos novos.

- [ ] **Step 1: Ajustar os testes existentes para a nova assinatura**

Reescrever `src/test/java/com/marmore/api/image/service/ImageEditServiceTest.java` inteiramente. As mudanças:

1. Adicionar `@Autowired ImageResizer resizer;` (bean real do Spring).
2. Cada chamada `service.generate("prompt", List.of(imagem), EditOptions.defaults())` vira `service.generate(converterParaBytes(imagem))` onde `converterParaBytes` lê o `Resource` para `byte[]`.
3. Remover imports não usados (`EditOptions`, `Resource` onde aplicável).
4. Adicionar `props.setStonePath(Path)` apontando para a `test-images/ambiente.png` como se fosse a pedra (reaproveita o resource existente). Importante: o `BeforeEach` precisa resetar `stonePath`.
5. Adicionar dois novos casos:
   - `erroQuandoPedraAusenteSemChamarApi`: `props.setStonePath(Paths.get("/tmp/nao-existe.png"))` → `Err` com "stone image not found".
   - `erroQuandoImagemIndecodificavelSemChamarApi`: passar `new byte[] {1, 2, 3}` → `Err` com "unable to decode input image".

Conteúdo completo do arquivo de teste:

```java
package com.marmore.api.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.marmore.api.image.config.ImageEditProperties;
import com.marmore.api.image.domain.GenerateResult;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

/** Testes de {@link ImageEditService}. */
@SpringBootTest
@AutoConfigureMockRestServiceServer
@TestPropertySource(
    properties = {
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-teste",
      "marmore.openai.image.timeout=5s"
    })
class ImageEditServiceTest {

  @Autowired ImageEditService service;
  @Autowired ImageEditProperties props;
  @Autowired MockRestServiceServer server;

  private static final Path PEDRA_VALIDA =
      new ClassPathResource("test-images/ambiente.png").getFile().toPath();

  ImageEditServiceTest() throws java.io.IOException {}

  @BeforeEach
  void resetEstado() {
    props.setApiKey("chave-teste");
    props.setStonePath(PEDRA_VALIDA);
    server.reset();
  }

  /** Caso #2: api-key vazia deve retornar Err sem chamar a API. */
  @Test
  void erroQuandoApiKeyVaziaSemChamarApi() throws Exception {
    props.setApiKey("");

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("OPENAI_API_KEY ausente");
    server.verify();
  }

  /** Caso #3: pedra (stone-path) inexistente deve retornar Err sem chamar a API. */
  @Test
  void erroQuandoPedraAusenteSemChamarApi() throws Exception {
    props.setStonePath(Paths.get("/tmp/arquivo-que-nao-existe.png"));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("stone image not found");
    server.verify();
  }

  /** Caso #3b: ambiente indecodificavel deve retornar Err sem chamar a API. */
  @Test
  void erroQuandoImagemIndecodificavelSemChamarApi() {
    GenerateResult result = service.generate(new byte[] {1, 2, 3, 4});

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("unable to decode input image");
    server.verify();
  }

  /** Caso #1: resposta com data[0].b64_json devolve Ok. */
  @Test
  void sucessoQuandoRespostaTemB64Json() throws Exception {
    String corpo = "{\"data\":[{\"b64_json\":\"aGVsbG8=\"}],\"usage\":{\"total_tokens\":10}}";
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    GenerateResult.Ok ok = (GenerateResult.Ok) result;
    assertThat(ok.b64()).isEqualTo("aGVsbG8=");
    assertThat(ok.usage()).isNotNull();
    server.verify();
  }

  /** Caso #4: resposta sem data[0] deve devolver Err. */
  @Test
  void erroQuandoRespostaSemData() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess("{\"foo\":1}", MediaType.APPLICATION_JSON));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).isEqualTo("resposta sem data[0]");
    server.verify();
  }

  /** Caso #5: resposta com data[0] mas sem b64_json deve devolver Err. */
  @Test
  void erroQuandoRespostaSemB64Json() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess("{\"data\":[{}]}", MediaType.APPLICATION_JSON));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).isEqualTo("resposta sem b64_json");
    server.verify();
  }

  /** Caso #6: erro HTTP deve devolver Err, nao propagar excecao. */
  @Test
  void erroQuandoServidorRespondeComErroHttp() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withBadRequest().body("{\"error\":{\"message\":\"bad model\"}}"));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    server.verify();
  }

  /** Caso #7: sucesso sem usage devolve Ok com usage nulo. */
  @Test
  void sucessoQuandoRespostaSemUsage() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess("{\"data\":[{\"b64_json\":\"eA==\"}]}", MediaType.APPLICATION_JSON));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    assertThat(((GenerateResult.Ok) result).usage()).isNull();
    server.verify();
  }

  /** Le a pedra de teste (ambiente.png do classpath) como bytes. */
  private static byte[] bytesDaPedraDeTeste() throws Exception {
    try (InputStream in = new ClassPathResource("test-images/ambiente.png").getInputStream()) {
      return in.readAllBytes();
    }
  }
}
```

- [ ] **Step 2: Rodar os testes para confirmar que tudo falha**

Run: `./mvnw test -Dtest=ImageEditServiceTest -q`
Expected: falha de compilação (assinatura `generate(byte[])` ainda não existe).

- [ ] **Step 3: Refatorar `ImageEditService` para a nova assinatura**

Reescrever `src/main/java/com/marmore/api/image/service/ImageEditService.java`:

```java
package com.marmore.api.image.service;

import com.marmore.api.image.config.ImageEditProperties;
import com.marmore.api.image.domain.EditOptions;
import com.marmore.api.image.domain.EditPrompts;
import com.marmore.api.image.domain.GenerateResult;
import java.nio.file.Paths;
import java.util.Optional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Servico de edicao de imagem via endpoint {@code /v1/images/edits} da OpenAI. Recebe apenas os
 * bytes da imagem do ambiente; injeta prompt fixo e imagem da pedra. Nenhum caminho lanca excecao:
 * falhas viram {@link GenerateResult.Err}.
 */
@Service
public class ImageEditService {

  private final ImageEditProperties props;
  private final RestClient restClient;
  private final ImageResizer resizer;

  /**
   * Construtor.
   *
   * @param props propriedades do modulo
   * @param restClient cliente HTTP autenticado
   * @param resizer redimensionador de imagem em memoria
   */
  public ImageEditService(
      ImageEditProperties props, RestClient restClient, ImageResizer resizer) {
    this.props = props;
    this.restClient = restClient;
    this.resizer = resizer;
  }

  /**
   * Gera/edita imagem a partir dos bytes do ambiente, injetando prompt fixo e imagem da pedra.
   *
   * @param ambiente bytes da foto do ambiente a ser editada
   * @return sucesso ou erro, nunca lanca
   */
  public GenerateResult generate(byte[] ambiente) {
    long start = System.nanoTime();
    if (props.getApiKey() == null || props.getApiKey().isBlank()) {
      return new GenerateResult.Err("OPENAI_API_KEY ausente. Defina no ambiente.", ms(start));
    }
    Resource pedra = new FileSystemResource(props.getStonePath());
    if (!pedra.exists()) {
      return new GenerateResult.Err(
          "stone image not found: " + props.getStonePath(), ms(start));
    }
    Optional<byte[]> ambienteReduzido = resizer.resize(ambiente);
    if (ambienteReduzido.isEmpty()) {
      return new GenerateResult.Err("unable to decode input image", ms(start));
    }
    try {
      EditOptions opts = EditOptions.defaults();
      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("model", opts.model());
      body.add("prompt", EditPrompts.COUNTERTOP);
      body.add("size", opts.size());
      body.add("quality", opts.quality());
      body.add("n", 1);
      body.add("image[]", new InMemoryResource(ambienteReduzido.get(), "ambiente.jpg"));
      body.add("image[]", pedra);
      if (opts.sendsFidelity()) {
        body.add("input_fidelity", opts.inputFidelity());
      }

      JsonNode raw =
          restClient
              .post()
              .uri("/v1/images/edits")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(body)
              .retrieve()
              .body(JsonNode.class);

      long latency = ms(start);
      JsonNode data = raw.path("data");
      if (!data.isArray() || data.isEmpty()) {
        return new GenerateResult.Err("resposta sem data[0]", latency);
      }
      JsonNode b64Node = data.get(0).path("b64_json");
      if (b64Node.isMissingNode()) {
        return new GenerateResult.Err("resposta sem b64_json", latency);
      }
      JsonNode usage = raw.has("usage") ? raw.get("usage") : null;
      return new GenerateResult.Ok(b64Node.asText(), raw, usage, latency);
    } catch (Exception e) {
      return new GenerateResult.Err(
          e.getClass().getSimpleName() + ": " + e.getMessage(), ms(start));
    }
  }

  private static long ms(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }

  /** ByteArrayResource com nome de arquivo, necessario para multipart. */
  private static final class InMemoryResource extends org.springframework.core.io.ByteArrayResource {
    private final String filename;

    InMemoryResource(byte[] bytes, String filename) {
      super(bytes);
      this.filename = filename;
    }

    @Override
    public String getFilename() {
      return filename;
    }
  }
}
```

- [ ] **Step 4: Rodar os testes do service para confirmar passagem**

Run: `./mvnw test -Dtest=ImageEditServiceTest -q`
Expected: PASS (8 testes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marmore/api/image/service/ImageEditService.java \
        src/test/java/com/marmore/api/image/service/ImageEditServiceTest.java
git commit -m "feat(image): injeta prompt fixo, pedra e resize no service"
```

> Nota: o `controller` ainda referencia a assinatura antiga, então a compilação do projeto vai falhar. Isso é resolvido na Task 7. Não rode `make verify` ainda.

---

## Task 7: Refatorar `ImageEditController` para `image` singular sem `prompt`

**Files:**
- Modify: `src/main/java/com/marmore/api/image/web/ImageEditController.java`
- Test: `src/test/java/com/marmore/api/image/web/ImageEditControllerTest.java`

**Interfaces:**
- Consumes: `ImageEditService.generate(byte[]) -> GenerateResult` (Task 6).
- Produces: `POST /images/edit` aceita `image` (singular), sem `prompt`.

- [ ] **Step 1: Atualizar o teste do controller**

Reescrever `src/test/java/com/marmore/api/image/web/ImageEditControllerTest.java`. Adicionar `props.setStonePath(...)` no `BeforeEach` apontando para `test-images/ambiente.png`. Trocar `.file(imagemPart)` (campo `images`) por `.file("image", ...)` ou `MockMultipartFile("image", ...)`. Remover `.param("prompt", ...)`.

Conteúdo completo:

```java
package com.marmore.api.image.web;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marmore.api.image.config.ImageEditProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

/** Testes de {@link ImageEditController}. */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureMockRestServiceServer
@TestPropertySource(
    properties = {
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-teste",
      "marmore.openai.image.timeout=5s"
    })
class ImageEditControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired MockRestServiceServer server;
  @Autowired ImageEditProperties props;

  private static final Path PEDRA_VALIDA;
  private static final byte[] PEDRA_BYTES;

  static {
    try {
      PEDRA_VALIDA = new ClassPathResource("test-images/ambiente.png").getFile().toPath();
      PEDRA_BYTES =
          new ClassPathResource("test-images/ambiente.png").getInputStream().readAllBytes();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @org.junit.jupiter.api.BeforeEach
  void resetEstado() {
    props.setApiKey("chave-teste");
    props.setStonePath(PEDRA_VALIDA);
    server.reset();
  }

  /** Sucesso: POST /images/edit devolve 200 com Content-Type image/png. */
  @Test
  void postDeveRetornarPngQuandoServidorOpenAiRespondeB64() throws Exception {
    byte[] imagemEsperada = java.util.Base64.getDecoder().decode("aGVsbG8=");
    String corpo = "{\"data\":[{\"b64_json\":\"aGVsbG8=\"}]}";
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc
        .perform(multipart("/images/edit").file(imagemPart))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(content().bytes(imagemEsperada));
  }

  /** Api-key ausente: POST /images/edit devolve 503. */
  @Test
  void postDeveRetornar503QuandoApiKeyVazia() throws Exception {
    props.setApiKey("");

    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc
        .perform(multipart("/images/edit").file(imagemPart))
        .andExpect(status().isServiceUnavailable());
  }

  /** Pedra ausente: POST /images/edit devolve 503. */
  @Test
  void postDeveRetornar503QuandoPedraAusente() throws Exception {
    props.setStonePath(java.nio.file.Paths.get("/tmp/nao-existe.png"));

    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc
        .perform(multipart("/images/edit").file(imagemPart))
        .andExpect(status().isServiceUnavailable());
  }

  /** Erro HTTP da OpenAI: POST /images/edit devolve 502. */
  @Test
  void postDeveRetornar502QuandoServidorOpenAiRespondeErro() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(
            org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest()
                .body("{\"error\":{\"message\":\"bad model\"}}"));
    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc
        .perform(multipart("/images/edit").file(imagemPart))
        .andExpect(status().isBadGateway());
  }
}
```

- [ ] **Step 2: Rodar os testes para confirmar falha**

Run: `./mvnw test -Dtest=ImageEditControllerTest -q`
Expected: falha (controller ainda espera `prompt` e `images`).

- [ ] **Step 3: Refatorar o controller**

Reescrever `src/main/java/com/marmore/api/image/web/ImageEditController.java`:

```java
package com.marmore.api.image.web;

import com.marmore.api.image.domain.GenerateResult;
import com.marmore.api.image.service.ImageEditService;
import java.io.IOException;
import java.util.Base64;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoint HTTP para edicao de imagem. Recebe apenas a foto do ambiente; o prompt fixo e a imagem
 * da pedra sao injetados pelo service. Devolve o PNG resultante do {@code /v1/images/edits} da
 * OpenAI.
 */
@RestController
@RequestMapping("/images")
public class ImageEditController {

  private final ImageEditService service;

  /**
   * Construtor.
   *
   * @param service servico de edicao
   */
  public ImageEditController(ImageEditService service) {
    this.service = service;
  }

  /**
   * POST /images/edit.
   *
   * @param image foto do ambiente a ser editada
   * @return PNG gerado (200) ou erro (4xx/5xx)
   * @throws IOException se falhar a leitura do MultipartFile
   */
  @PostMapping(value = "/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> edit(@RequestParam("image") MultipartFile image)
      throws IOException {
    GenerateResult resultado = service.generate(image.getBytes());
    if (resultado instanceof GenerateResult.Err err) {
      throw new ImageEditException(statusPara(err.error()), err.error());
    }
    GenerateResult.Ok ok = (GenerateResult.Ok) resultado;
    byte[] png = Base64.getDecoder().decode(ok.b64());
    return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
  }

  /**
   * Mapeia a mensagem de erro para status HTTP apropriado.
   *
   * @param mensagem mensagem vinda do {@link GenerateResult.Err}
   * @return status HTTP correspondente
   */
  private static org.springframework.http.HttpStatus statusPara(String mensagem) {
    if (mensagem.startsWith("OPENAI_API_KEY")) {
      return org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
    }
    if (mensagem.startsWith("stone image not found")) {
      return org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
    }
    if (mensagem.startsWith("unable to decode input image")) {
      return org.springframework.http.HttpStatus.BAD_REQUEST;
    }
    return org.springframework.http.HttpStatus.BAD_GATEWAY;
  }
}
```

- [ ] **Step 4: Rodar os testes do controller**

Run: `./mvnw test -Dtest=ImageEditControllerTest -q`
Expected: PASS (4 testes).

- [ ] **Step 5: Formatar e rodar verify completo**

Run: `make format && make verify`
Expected: `BUILD SUCCESS`, `Tests run: N, Failures: 0, Errors: 0`, `0 Checkstyle violations`, `Spotless keeping N files clean`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/marmore/api/image/web/ImageEditController.java \
        src/test/java/com/marmore/api/image/web/ImageEditControllerTest.java
git commit -m "feat(image): troca endpoint para receber apenas image"
```

---

## Task 8: Atualizar a collection Bruno

**Files:**
- Modify: `bruno/marmore-api/editar_imagem.bru`

**Interfaces:**
- Sem testes automatizados. Validação manual via Bruno.

- [ ] **Step 1: Reescrever o `.bru`**

Substituir o bloco `body:multipart-form` para conter apenas a imagem singular:

```
meta {
  name: Editar Imagem
  type: http
  seq: 1
}

post {
  url: {{base_url}}/images/edit
  body: multipart-form
  auth: none
}

headers {
  Accept: image/png
}

body:multipart-form {
  image: @file(./assets/ambiente.jpg)
}

script:post-response {
  const status = res.getStatus();
  if (status === 200) {
    console.log("Imagem gerada, tamanho (bytes):", res.getBody().length);
  } else {
    console.log("Falha. Status:", status, "Body:", res.getBody());
  }
}

tests {
  test("deve retornar 200 OK em sucesso", function() {
    expect(res.getStatus()).to.equal(200);
  });

  test("deve retornar content-type image/png", function() {
    expect(res.getHeader("Content-Type")).to.include("image/png");
  });
}

settings {
  encodeUrl: true
  timeout: 0
}
```

- [ ] **Step 2: Commit**

```bash
git add bruno/marmore-api/editar_imagem.bru
git commit -m "chore(bruno): troca request para apenas image"
```

---

## Task 9: Documentação final — `CHANGELOG.md`

**Files:**
- Modify: `CHANGELOG.md`

**Interfaces:**
- Nenhum teste.

- [ ] **Step 1: Atualizar o changelog**

Em `CHANGELOG.md`, na seção `[Não publicado]`, registrar breaking change do endpoint:

Adicionar sob a seção `[Não publicado]`:

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs(image): documenta breaking change do endpoint"
```

---

## Self-Review (executado pelo autor do plano)

**1. Cobertura da spec:**
- Endpoint só com `image`, sem prompt → Tasks 7 + 8. ✓
- Prompt fixo como constante → Task 2. ✓
- Pedra via `stone-path` no yaml → Tasks 4 + 5. ✓
- Texto do prompt = `prompt.md` → Task 2 (passo de copiar do arquivo). ✓
- Campo multipart singular `image` no cliente → Task 8. ✓
- Multipart `image[]` em ordem `[ambiente, pedra]` para OpenAI → Task 6. ✓
- Pedra ausente → 503 → Tasks 6 + 7. ✓
- Imagem indecodificável → 400 → Tasks 6 + 7. ✓
- Upload > 25MB → 413 (Spring) → Task 5. ✓
- Erro OpenAI → 502 → Tasks 6 + 7. ✓
- Api-key ausente → 503 → Tasks 6 + 7. ✓
- Thumbnailator 0.4.21 no pom → Task 1. ✓
- `EditPrompts`, `ImageResizer`, `stonePath` → Tasks 2, 3, 4. ✓
- Bruno `.bru` → Task 8. ✓

**2. Placeholders:** Sem "TBD". Único passo com "colar conteúdo de prompt.md" é legítimo — texto longo que deve ser copiado da fonte canônica. Preferível a duplicar 20 linhas aqui.

**3. Consistência de tipos:**
- `ImageEditService.generate(byte[] ambiente)` assinatura usada idêntica em Task 6 (definição), Task 7 (controller chama `service.generate(image.getBytes())`). ✓
- `ImageResizer.resize(byte[]) -> Optional<byte[]>` definido em Task 3 e consumido em Task 6. ✓
- `EditPrompts.COUNTERTOP` definido em Task 2, usado em Task 6. ✓
- `props.getStonePath() -> Path` definido em Task 4, usado em Tasks 6 e 7. ✓

**4. Ordem de compilação:** Tasks 1-5 não quebram o build. Task 6 quebra a compilação (controller chama `generate(prompt, images, opts)`). Task 7 conserta o controller. Self-contained.

## Execution Handoff

Plano completo e salvo em `docs/superpowers/plans/2026-07-21-edicao-imagem-somente-imagem.md`. Duas opções de execução:

1. **Subagent-Driven (recomendado)** — Dispatch de um subagente novo por task, com review entre tasks. Iteração rápida, isolamento de contexto.
2. **Inline Execution** — Executo as tasks nesta sessão, com checkpoints para review a cada bloco.

Qual abordagem?
