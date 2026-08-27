package com.marmore.api.imageedit.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Router funcional (WebFlux) que expoe o historico das imagens geradas: {@code GET /images} com o
 * JSON array de {@link ImageGenerationSummary} produzido pelo {@link ImageHistoryHandler}.
 */
@Configuration
public class ImageHistoryRouter {

  /**
   * Bean do router do historico.
   *
   * @param handler handler reativo injetado pelo Spring
   * @return {@link RouterFunction} mapeando {@code GET /images}
   */
  @Bean
  public RouterFunction<ServerResponse> imageHistoryRoute(ImageHistoryHandler handler) {
    return RouterFunctions.route().GET("/images", handler::list).build();
  }
}
