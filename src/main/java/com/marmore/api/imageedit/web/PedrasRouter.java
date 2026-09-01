package com.marmore.api.imageedit.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Router funcional (WebFlux) que expoe o catalogo de pedras: {@code GET /pedras} com o JSON array
 * de {@link PedraSummary} e {@code GET /pedras/{id}/imagem} com os bytes da imagem, ambos
 * produzidos pelo {@link PedrasHandler}.
 */
@Configuration
public class PedrasRouter {

  /**
   * Bean do router do catalogo de pedras.
   *
   * @param handler handler reativo injetado pelo Spring
   * @return {@link RouterFunction} mapeando {@code GET /pedras} e {@code GET /pedras/{id}/imagem}
   */
  @Bean
  public RouterFunction<ServerResponse> pedrasRoute(PedrasHandler handler) {
    return RouterFunctions.route()
        .GET("/pedras", handler::list)
        .GET("/pedras/{id}/imagem", handler::imagem)
        .build();
  }
}
