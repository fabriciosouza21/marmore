package com.marmore.api.image.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Testes de {@link ImageEditProperties}. */
@SpringBootTest(
    properties = {
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-teste",
      "marmore.openai.image.default-model=gpt-image-1.5",
      "marmore.openai.image.timeout=30s"
    })
class ImageEditPropertiesTest {

  @Autowired ImageEditProperties props;

  @Test
  void bindResolvePropriedades() {
    assertThat(props.getBaseUrl()).isEqualTo("https://example.test");
    assertThat(props.getApiKey()).isEqualTo("chave-teste");
    assertThat(props.getDefaultModel()).isEqualTo("gpt-image-1.5");
    assertThat(props.getTimeout()).isEqualTo(Duration.ofSeconds(30));
  }
}
