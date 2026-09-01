package com.marmore.api.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Router funcional (WebFlux) que expoe o healthcheck publico: {@code GET /health} com o JSON de
 * {@link HealthHandler} (liveness + versao do build).
 */
@Configuration
public class HealthRouter {

  /**
   * Bean do router de health.
   *
   * @param handler handler reativo injetado pelo Spring
   * @return {@link RouterFunction} mapeando {@code GET /health}
   */
  @Bean
  public RouterFunction<ServerResponse> healthRoute(HealthHandler handler) {
    return RouterFunctions.route().GET("/health", handler::health).build();
  }
}
