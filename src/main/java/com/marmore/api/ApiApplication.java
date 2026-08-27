package com.marmore.api;

import com.marmore.api.imageedit.config.ImageEditProperties;
import com.marmore.api.imageedit.cost.UsdBrlProperties;
import com.marmore.api.imageedit.storage.StorageProperties;
import com.marmore.api.security.ApiKeyProperties;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import reactor.core.publisher.Hooks;

/** Ponto de entrada da aplicação marmore-api. */
@EnableConfigurationProperties({
  ImageEditProperties.class,
  ApiKeyProperties.class,
  UsdBrlProperties.class,
  StorageProperties.class
})
@SpringBootApplication
public class ApiApplication {

  /**
   * Inicializa a aplicação Spring Boot.
   *
   * <p>Registra {@link Hooks#onErrorDropped} para registrar em {@code debug} (nao em {@code error})
   * os erros descartados pelo Reactor durante cancelamentos de stream. Sem isso, o cancelamento do
   * fluxo SSE apos o evento terminal ({@code .next()} cancela o upstream) gera um {@code
   * IllegalReferenceCountException} logado em nivel ERROR a cada request bem-sucedido.
   *
   * @param args argumentos de linha de comando
   */
  public static void main(String[] args) {
    Hooks.onErrorDropped(
        e ->
            LoggerFactory.getLogger("reactor.drop")
                .debug("erro descartado no cancelamento do stream: {}", e.toString()));
    SpringApplication.run(ApiApplication.class, args);
  }
}
