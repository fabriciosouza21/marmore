package com.marmore.api.image.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuracao do {@link RestClient} para chamadas a OpenAI. Define baseUrl e header de autorizacao
 * (Bearer). O read timeout e controlado pelas propriedades do Spring Boot ({@code
 * spring.http.client.read-timeout}).
 */
@Configuration
public class RestClientConfig {

  /**
   * Cria o bean RestClient autenticado para a API de imagens.
   *
   * @param props propriedades de configuracao do modulo
   * @param builder builder de RestClient provido pelo Spring Boot (permite customizacao por testes
   *     via RestClientCustomizer)
   * @return RestClient configurado
   */
  @Bean
  public RestClient imageRestClient(ImageEditProperties props, RestClient.Builder builder) {
    return builder
        .baseUrl(props.getBaseUrl())
        .defaultHeader("Authorization", "Bearer " + props.getApiKey())
        .build();
  }
}
