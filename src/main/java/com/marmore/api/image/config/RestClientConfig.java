package com.marmore.api.image.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuracao do {@link RestClient} para chamadas a OpenAI. Define baseUrl, header de autorizacao
 * (Bearer) e read timeout alto (default 180s).
 */
@Configuration
public class RestClientConfig {

  /**
   * Cria o bean RestClient autenticado para a API de imagens.
   *
   * @param props propriedades de configuracao do modulo
   * @return RestClient configurado
   */
  @Bean
  public RestClient imageRestClient(ImageEditProperties props) {
    HttpClientSettings settings = HttpClientSettings.defaults().withReadTimeout(props.getTimeout());

    return RestClient.builder()
        .baseUrl(props.getBaseUrl())
        .defaultHeader("Authorization", "Bearer " + props.getApiKey())
        .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
        .build();
  }
}
