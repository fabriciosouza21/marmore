package com.marmore.api.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.marmore.api.image.config.ImageEditProperties;
import com.marmore.api.image.domain.EditPrompts;
import com.marmore.api.image.domain.GenerateResult;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.client.RequestMatcher;

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

  /** Caminho da pedra de teste (ambiente.png do classpath), resolvido em runtime. */
  private static Path pedraValida() {
    try {
      return new ClassPathResource("test-images/ambiente.png").getFile().toPath();
    } catch (java.io.IOException e) {
      throw new IllegalStateException("ambiente.png ausente do classpath", e);
    }
  }

  @BeforeEach
  void resetEstado() {
    props.setApiKey("chave-teste");
    props.setStonePath(pedraValida());
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

  /**
   * Caso #6: erro HTTP deve devolver Err, nao propagar excecao. O body de erro da OpenAI deve ser
   * repassado na mensagem (Fix C), junto do status code.
   */
  @Test
  void erroQuandoServidorRespondeComErroHttp() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withBadRequest().body("{\"error\":{\"message\":\"bad model\"}}"));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    String error = ((GenerateResult.Err) result).error();
    assertThat(error).contains("400");
    assertThat(error).contains("{\"error\":{\"message\":\"bad model\"}}");
    server.verify();
  }

  /** Caso #7: sucesso sem usage devolve Ok com usage nulo. */
  @Test
  void sucessoQuandoRespostaSemUsage() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(
            withSuccess("{\"data\":[{\"b64_json\":\"eA==\"}]}", MediaType.APPLICATION_JSON));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    assertThat(((GenerateResult.Ok) result).usage()).isNull();
    server.verify();
  }

  /**
   * Caso #8 (contrato multipart): verifica que o body enviado a OpenAI e multipart, com dois campos
   * {@code image[]} na ordem ambiente.jpg -> pedra (ambiente.png), e que o prompt injetado contem
   * um trecho fixo de {@link EditPrompts#COUNTERTOP}. Trava a ordem das chamadas {@code
   * body.add(...)} no service.
   */
  @Test
  // CHECKSTYLE.SUPPRESS: AbbreviationAsWordInName for +1 lines
  void multipartEnviaDuasPartesImageNaOrdemAmbientePedraEPromptFixo() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andExpect(verificaMultipartAmbientePedraEPrompt())
        .andRespond(
            withSuccess("{\"data\":[{\"b64_json\":\"aGVsbG8=\"}]}", MediaType.APPLICATION_JSON));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    server.verify();
  }

  /**
   * Matcher customizado: inspeciona o body da {@link
   * org.springframework.http.client.ClientHttpRequest} como string e verifica o contrato multipart
   * (duas partes {@code image[]}, ordem ambiente/pedra, e presenca de trecho fixo do prompt).
   */
  // CHECKSTYLE.SUPPRESS: AbbreviationAsWordInName for +1 lines
  private static RequestMatcher verificaMultipartAmbientePedraEPrompt() {
    return request -> {
      Object rawBody = request.getBody();
      assertThat(rawBody).isInstanceOf(ByteArrayOutputStream.class);
      String body =
          new String(((ByteArrayOutputStream) rawBody).toByteArray(), StandardCharsets.UTF_8);

      String disposition = "Content-Disposition: form-data; name=\"image[]\"";
      int primeira = body.indexOf(disposition);
      int segunda = body.indexOf(disposition, primeira + disposition.length());
      assertThat(primeira).as("deve existir uma primeira parte image[]").isGreaterThanOrEqualTo(0);
      assertThat(segunda).as("deve existir uma segunda parte image[]").isGreaterThan(primeira);

      int primeiraFim = body.indexOf("\r\n", primeira);
      int segundaFim = body.indexOf("\r\n", segunda);
      String primeiraLinha = body.substring(primeira, primeiraFim);
      String segundaLinha = body.substring(segunda, segundaFim);
      assertThat(primeiraLinha).contains("filename=\"ambiente.jpg\"");
      assertThat(segundaLinha).contains("filename=\"ambiente.png\"");

      assertThat(body)
          .as("prompt enviado deve conter trecho fixo de EditPrompts.COUNTERTOP")
          .contains("SUNKEN DRAINBOARD");
    };
  }

  /** Le a pedra de teste (ambiente.png do classpath) como bytes. */
  private static byte[] bytesDaPedraDeTeste() throws Exception {
    try (InputStream in = new ClassPathResource("test-images/ambiente.png").getInputStream()) {
      return in.readAllBytes();
    }
  }
}
