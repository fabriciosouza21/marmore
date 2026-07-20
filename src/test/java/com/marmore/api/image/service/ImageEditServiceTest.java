package com.marmore.api.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.marmore.api.image.domain.EditOptions;
import com.marmore.api.image.domain.GenerateResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

/** Testes de {@link ImageEditService}. */
@SpringBootTest
@AutoConfigureMockRestServiceServer
@TestPropertySource(
    properties = {
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=",
      "marmore.openai.image.timeout=5s"
    })
class ImageEditServiceTest {

  @Autowired ImageEditService service;
  @Autowired MockRestServiceServer server;

  /** Caso #2: api-key vazia deve retornar Err sem chamar a API. */
  @Test
  void erroQuandoApiKeyVaziaSemChamarApi() {
    GenerateResult result = service.generate("prompt", List.of(), EditOptions.defaults());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("OPENAI_API_KEY ausente");
    server.verify();
  }
}
