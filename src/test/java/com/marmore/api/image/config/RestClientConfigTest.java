package com.marmore.api.image.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

/** Testes do bean {@link RestClient} configurado por {@link RestClientConfig}. */
@SpringBootTest(
    properties = {
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-teste"
    })
class RestClientConfigTest {

  @Autowired RestClient restClient;

  @Test
  void beanRestClientDisponivel() {
    assertThat(restClient).isNotNull();
  }
}
