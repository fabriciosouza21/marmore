package com.marmore.api.image.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.marmore.api.imageedit.ai.AiImageException;
import com.marmore.api.imageedit.ai.AiImageOptions;
import com.marmore.api.imageedit.ai.ImageEditPrompt;
import com.marmore.api.imageedit.ai.ImageResponse;
import com.marmore.api.imageedit.ai.InputImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;

/** Testes de {@link OpenAiRestClientImageEditModel}. */
@SpringBootTest
@AutoConfigureMockRestServiceServer
@TestPropertySource(
    properties = {
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-teste",
      "marmore.openai.image.timeout=5s"
    })
class OpenAiRestClientImageEditModelTest {

  @Autowired OpenAiRestClientImageEditModel model;
  @Autowired MockRestServiceServer server;

  @BeforeEach
  void resetEstado() {
    server.reset();
  }

  /** Caso #1: resposta com data[0].b64_json devolve ImageResponse com primeira geracao. */
  @Test
  void callDevolveRespostaQuandoOpenaiRetornaB64() throws Exception {
    String corpo = "{\"data\":[{\"b64_json\":\"aGVsbG8=\"}],\"usage\":{\"total_tokens\":10}}";
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

    ImageResponse resp = model.call(promptSimples());

    assertThat(resp.getResult()).isNotNull();
    assertThat(resp.getResult().output().b64Json()).isEqualTo("aGVsbG8=");
    assertThat(resp.metadata().usage()).isNotNull();
  }

  /** Caso #4: resposta sem data[0] deve lancar AiImageException. */
  @Test
  void callLancaQuandoRespostaSemData() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess("{\"foo\":1}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> model.call(promptSimples()))
        .isInstanceOf(AiImageException.class)
        .hasMessageContaining("resposta sem data[0]");
  }

  /** Caso #5: resposta com data[0] mas sem b64_json deve lancar AiImageException. */
  @Test
  void callLancaQuandoRespostaSemB64Json() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess("{\"data\":[{}]}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> model.call(promptSimples()))
        .isInstanceOf(AiImageException.class)
        .hasMessageContaining("resposta sem b64_json");
  }

  /** Caso #6: erro HTTP deve lancar AiImageException com status e body. */
  @Test
  void callLancaQuandoServidorRespondeComErroHttp() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withBadRequest().body("{\"error\":{\"message\":\"bad model\"}}"));

    assertThatThrownBy(() -> model.call(promptSimples()))
        .isInstanceOf(AiImageException.class)
        .hasMessageContaining("400")
        .hasMessageContaining("{\"error\":{\"message\":\"bad model\"}}");
  }

  /** Caso #7: sucesso sem usage devolve metadata com usage nulo. */
  @Test
  void callDevolveMetadataSemUsageQuandoAusente() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(
            withSuccess("{\"data\":[{\"b64_json\":\"eA==\"}]}", MediaType.APPLICATION_JSON));

    ImageResponse resp = model.call(promptSimples());

    assertThat(resp.metadata().usage()).isNull();
  }

  /**
   * Caso #8 (contrato multipart): o body enviado a OpenAI deve conter duas partes {@code image[]}
   * na ordem ambiente -> pedra, e o prompt injetado. Trava a ordem das chamadas {@code body.add}.
   */
  @Test
  void callEnviaDuasPartesImageNaOrdemAmbientePedraComPrompt() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andExpect(verificaMultipartAmbientePedraEPrompt())
        .andRespond(
            withSuccess("{\"data\":[{\"b64_json\":\"aGVsbG8=\"}]}", MediaType.APPLICATION_JSON));

    ImageResponse resp = model.call(promptComDuasImagens());

    assertThat(resp.getResult().output().b64Json()).isEqualTo("aGVsbG8=");
  }

  /** Prompt com uma imagem apenas (cenario minimo). */
  private static ImageEditPrompt promptSimples() {
    return ImageEditPrompt.of(
        "prompt simples",
        AiImageOptions.defaults(),
        List.of(InputImage.of(new byte[] {1, 2, 3}, "ambiente.jpg")));
  }

  /** Prompt com duas imagens (ambiente + pedra), na ordem semantica. */
  private static ImageEditPrompt promptComDuasImagens() {
    return ImageEditPrompt.of(
        "SUNKEN DRAINBOARD prompt",
        AiImageOptions.defaults(),
        List.of(
            InputImage.of(new byte[] {1, 2, 3}, "ambiente.jpg"),
            InputImage.of(new byte[] {4, 5, 6}, "granito.png")));
  }

  /**
   * Matcher: inspeciona o body do request como string e verifica o contrato multipart (duas partes
   * {@code image[]}, ordem ambiente/pedra, e trecho do prompt).
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
      assertThat(segundaLinha).contains("filename=\"granito.png\"");

      assertThat(body).as("prompt enviado deve conter trecho fixo").contains("SUNKEN DRAINBOARD");
    };
  }
}
