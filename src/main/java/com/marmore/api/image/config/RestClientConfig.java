package com.marmore.api.image.config;

import com.marmore.api.imageedit.config.ImageEditProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.client.RestClient;

/**
 * Configuracao do {@link RestClient} para chamadas a OpenAI. Define baseUrl e header de autorizacao
 * (Bearer) no bean. O read timeout e aplicado via {@link RestClientCustomizer} (e nao via {@code
 * requestFactory} direto no builder) para que o {@code MockRestServiceServer} dos testes consiga
 * envolver o factory sem conflito de ordem (issue spring-projects/spring-boot#38832). A propriedade
 * do modulo {@link ImageEditProperties#getTimeout()} e a fonte unica da verdade.
 */
@Configuration
public class RestClientConfig {

  /**
   * Customizer que aplica o read timeout do modulo ao factory do RestClient. Ordenado com baixa
   * precedencia para rodar antes do customizer do MockRestServiceServer em testes.
   *
   * @param props propriedades de configuracao do modulo
   * @return customizer de timeout
   */
  @Bean
  @Order(0)
  public RestClientCustomizer imageReadTimeoutCustomizer(ImageEditProperties props) {
    HttpClientSettings settings = HttpClientSettings.defaults().withReadTimeout(props.getTimeout());
    return builder ->
        builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
  }

  /**
   * Cria o bean RestClient autenticado para a API de imagens.
   *
   * @param props propriedades de configuracao do modulo
   * @param builder builder de RestClient provido pelo Spring Boot (customizers, incluindo o de
   *     timeout, sao aplicados pelo proprio builder)
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
