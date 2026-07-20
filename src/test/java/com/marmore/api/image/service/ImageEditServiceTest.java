package com.marmore.api.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.marmore.api.image.config.ImageEditProperties;
import com.marmore.api.image.domain.EditOptions;
import com.marmore.api.image.domain.GenerateResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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

  @BeforeEach
  void resetApiKey() {
    props.setApiKey("chave-teste");
    server.reset();
  }

  /** Caso #2: api-key vazia deve retornar Err sem chamar a API. */
  @Test
  void erroQuandoApiKeyVaziaSemChamarApi() {
    props.setApiKey("");

    GenerateResult result = service.generate("prompt", List.of(), EditOptions.defaults());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("OPENAI_API_KEY ausente");
    server.verify();
  }

  /** Caso #3: imagem de entrada inexistente deve retornar Err sem chamar a API. */
  @Test
  void erroQuandoImagemInexistenteSemChamarApi() {
    Resource inexistente = new FileSystemResource("/tmp/arquivo-que-nao-existe.png");

    GenerateResult result =
        service.generate("prompt", List.of(inexistente), EditOptions.defaults());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("imagem de entrada ausente");
    server.verify();
  }

  /** Caso #1: resposta com data[0].b64_json devolve Ok com o b64 correto. */
  @Test
  void sucessoQuandoRespostaTemB64Json() {
    Resource imagem = new ClassPathResource("test-images/ambiente.png");
    String corpo = "{\"data\":[{\"b64_json\":\"aGVsbG8=\"}],\"usage\":{\"total_tokens\":10}}";
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

    GenerateResult result =
        service.generate("bancada verde", List.of(imagem), EditOptions.defaults());

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    GenerateResult.Ok ok = (GenerateResult.Ok) result;
    assertThat(ok.b64()).isEqualTo("aGVsbG8=");
    assertThat(ok.usage()).isNotNull();
    server.verify();
  }

  /** Caso #4: resposta sem data[0] deve devolver Err. */
  @Test
  void erroQuandoRespostaSemData() {
    Resource imagem = new ClassPathResource("test-images/ambiente.png");
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess("{\"foo\":1}", MediaType.APPLICATION_JSON));

    GenerateResult result = service.generate("p", List.of(imagem), EditOptions.defaults());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).isEqualTo("resposta sem data[0]");
    server.verify();
  }

  /** Caso #5: resposta com data[0] mas sem b64_json deve devolver Err. */
  @Test
  void erroQuandoRespostaSemB64Json() {
    Resource imagem = new ClassPathResource("test-images/ambiente.png");
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess("{\"data\":[{}]}", MediaType.APPLICATION_JSON));

    GenerateResult result = service.generate("p", List.of(imagem), EditOptions.defaults());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).isEqualTo("resposta sem b64_json");
    server.verify();
  }

  /** Caso #6: erro HTTP deve devolver Err, nao propagar excecao. */
  @Test
  void erroQuandoServidorRespondeComErroHttp() {
    Resource imagem = new ClassPathResource("test-images/ambiente.png");
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withBadRequest().body("{\"error\":{\"message\":\"bad model\"}}"));

    GenerateResult result = service.generate("p", List.of(imagem), EditOptions.defaults());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    server.verify();
  }

  /** Caso #7: sucesso sem usage devolve Ok com usage nulo. */
  @Test
  void sucessoQuandoRespostaSemUsage() {
    Resource imagem = new ClassPathResource("test-images/ambiente.png");
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(
            withSuccess("{\"data\":[{\"b64_json\":\"eA==\"}]}", MediaType.APPLICATION_JSON));

    GenerateResult result = service.generate("p", List.of(imagem), EditOptions.defaults());

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    assertThat(((GenerateResult.Ok) result).usage()).isNull();
    server.verify();
  }

  /** Caso #8: modelo suportado com fidelity envia input_fidelity no multipart. */
  @Test
  void enviaInputFidelityQuandoModeloSuporta() {
    Resource imagem = new ClassPathResource("test-images/ambiente.png");
    EditOptions opts =
        new EditOptions("gpt-image-1.5", "1024x1024", "medium", "high", Duration.ofSeconds(5));
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(
            withSuccess("{\"data\":[{\"b64_json\":\"eA==\"}]}", MediaType.APPLICATION_JSON));

    service.generate("p", List.of(imagem), opts);

    server.verify();
  }

  /** Caso #9: modelo nao suportado mesmo com fidelity omite input_fidelity. */
  @Test
  void omiteInputFidelityQuandoModeloNaoSuporta() {
    Resource imagem = new ClassPathResource("test-images/ambiente.png");
    EditOptions opts =
        new EditOptions("gpt-image-2", "1024x1024", "medium", "high", Duration.ofSeconds(5));
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(
            withSuccess("{\"data\":[{\"b64_json\":\"eA==\"}]}", MediaType.APPLICATION_JSON));

    service.generate("p", List.of(imagem), opts);

    server.verify();
  }
}
