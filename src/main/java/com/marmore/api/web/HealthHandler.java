package com.marmore.api.web;

import java.util.Map;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Handler de liveness/versao do {@code GET /health}. Responde {@code {"status":"ok","version":...}}
 * com a versao do build ({@link BuildProperties}, gerada pelo goal {@code build-info} do
 * spring-boot-maven-plugin a partir do {@code <version>} do pom). Endpoint publico (sem {@code
 * X-API-Key}): usado pelo healthcheck do container e para conferir a versao no ar.
 */
@Component
public class HealthHandler {

  private final BuildProperties build;

  /**
   * Construtor.
   *
   * @param build metadados de build com a versao da aplicacao
   */
  public HealthHandler(BuildProperties build) {
    this.build = build;
  }

  /**
   * Responde o estado e a versao.
   *
   * @param request requisicao (ignorada)
   * @return 200 com JSON {@code status}/{@code version}
   */
  public Mono<ServerResponse> health(ServerRequest request) {
    return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("status", "ok", "version", build.getVersion()));
  }
}
