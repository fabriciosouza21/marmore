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
        .andRespond(
            withSuccess("{\"data\":[{\"b64_json\":\"eA==\"}]}", MediaType.APPLICATION_JSON));

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
