package com.marmore.api.imageedit.web;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Router funcional (WebFlux) que expoe o endpoint de edicao de imagem como stream SSE. Mapeia
 * {@code POST /images/edit}, consumindo {@code multipart/form-data} e aceitando resposta em {@code
 * text/event-stream}.
 *
 * <p>Coexiste com o {@link com.marmore.api.image.web.ImageEditController} legado (annotation-based,
 * servlet/MVC) sem conflito enquanto o app roda no hibrido servlet+webflux: em modo servlet o
 * dispatcher do Spring MVC atende o controller anotado e o RouterFunction so e considerado em modo
 * WebFlux puro (Task 16). RouterFunction beans sao ignorados pelo {@code DispatcherHandler} do
 * Spring MVC.
 */
@Configuration
public class ImageEditRouter {

  /**
   * Bean do router do endpoint de edicao.
   *
   * @param handler handler reativo injetado pelo Spring
   * @return {@link RouterFunction} mapeando {@code POST /images/edit}
   */
  @Bean
  public RouterFunction<ServerResponse> imageEditRoute(ImageEditHandler handler) {
    return RouterFunctions.route(
        POST("/images/edit")
            .and(contentType(MediaType.MULTIPART_FORM_DATA))
            .and(accept(MediaType.TEXT_EVENT_STREAM)),
        handler::edit);
  }
}
