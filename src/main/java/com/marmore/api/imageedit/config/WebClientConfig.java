package com.marmore.api.imageedit.config;

import com.marmore.api.imageedit.ai.OpenAiWebClientImageEditModel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Configuracao do {@link WebClient} reativo para chamadas a OpenAI. Substitui o antigo {@code
 * RestClientConfig} (sincrono). O bean {@code imageWebClient} carrega {@code baseUrl} e o header
 * {@code Authorization: Bearer <apiKey>}; o read timeout e aplicado no Netty via {@link
 * ReadTimeoutHandler} no {@link HttpClient}, equivalente reativo do que antes era feito por {@code
 * RestClientCustomizer}. Aqui tambem vivem os wiring do gateway reativo {@link
 * OpenAiWebClientImageEditModel}, unico {@code ImageEditModel} apos a remocao do gateway sincrono
 * legado.
 */
@Configuration
public class WebClientConfig {

  /**
   * Cria o bean WebClient autenticado para a API de imagens.
   *
   * <p>O {@link ReadTimeoutHandler} e adicionado em {@code doOnConnected} para que cada conexao
   * recem-estabelecida receba o handler de read timeout no pipeline Netty. A duracao e convertida
   * para millis (em vez de segundos) para preservar precisao sub-segundo definida em {@link
   * ImageEditProperties#getTimeout()}.
   *
   * @param props propriedades de configuracao do modulo (baseUrl, apiKey, timeout)
   * @param builder builder de WebClient provido pelo Spring Boot (autoconfigurado com codecs, etc.)
   * @return WebClient configurado
   */
  @Bean
  public WebClient imageWebClient(ImageEditProperties props, WebClient.Builder builder) {
    long timeoutMs = props.getTimeout().toMillis();
    HttpClient httpClient =
        HttpClient.create()
            .doOnConnected(
                conn ->
                    conn.addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));
    return builder
        .baseUrl(props.getBaseUrl())
        .defaultHeader("Authorization", "Bearer " + props.getApiKey())
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
        .build();
  }

  /**
   * Expoe o gateway reativo de edicao de imagem como bean, substituindo o antigo {@code
   * OpenAiRestClientImageEditModel}. A classe em si nao e {@code @Component} (por escolha da Task
   * 9, que deferiu o wiring para a Task 10); o registro explicito aqui mantem a criacao do bean
   * proxima da do {@link WebClient} que ele consome.
   *
   * @param imageWebClient cliente HTTP autenticado (bean {@code imageWebClient})
   * @return gateway reativo
   */
  @Bean
  public OpenAiWebClientImageEditModel imageEditModel(WebClient imageWebClient) {
    return new OpenAiWebClientImageEditModel(imageWebClient);
  }
}
