package com.marmore.api.imageedit.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.marmore.api.imageedit.ai.OpenAiWebClientImageEditModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Testes do bean {@code imageWebClient} e do wiring do gateway {@link
 * OpenAiWebClientImageEditModel} em {@link WebClientConfig}. Usa {@link ApplicationContextRunner}
 * para isolar a configuracao sob teste do contexto completo da aplicacao: a aplicacao ainda tem o
 * {@code ImageEditService} legado (sincrono, {@code @Service}) que depende da interface {@code
 * image.ai.ImageEditModel} sem implementacao apos a remocao do gateway RestClient nesta task;
 * carregar o contexto completo quebraria o teste por uma falha de wiring alheia a este bean. O
 * {@code ImageEditService} sera reescrito na Task 11.
 */
class WebClientConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(WebClientConfig.class, WebClientAutoConfigImports.class)
          .withPropertyValues(
              "marmore.openai.image.base-url=https://example.test",
              "marmore.openai.image.api-key=chave-teste",
              "marmore.openai.image.timeout=5s",
              "marmore.openai.image.stone-path=/tmp/pedra.png");

  @DisplayName("bean imageWebClient e construido com properties validas")
  @Test
  void beanWebClientDisponivel() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(WebClient.class);
          assertThat(context.getBean(WebClient.class)).isNotNull();
        });
  }

  @DisplayName("gateway reativo OpenAiWebClientImageEditModel e registrado como bean")
  @Test
  void gatewayReativoInjetado() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(OpenAiWebClientImageEditModel.class);
          assertThat(context.getBean(OpenAiWebClientImageEditModel.class)).isNotNull();
        });
  }

  /**
   * Importa a autoconfiguracao que produz {@link
   * org.springframework.web.reactive.function.client.WebClient.Builder} (codec, Jackson, etc.) e
   * registra {@link ImageEditProperties} via {@link EnableConfigurationProperties}.
   */
  @org.springframework.context.annotation.Import(
      org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration.class)
  @EnableConfigurationProperties(ImageEditProperties.class)
  static class WebClientAutoConfigImports {}
}
