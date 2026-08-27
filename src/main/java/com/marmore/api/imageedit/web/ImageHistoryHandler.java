package com.marmore.api.imageedit.web;

import com.marmore.api.imageedit.storage.GeneratedImageRepository;
import com.marmore.api.imageedit.storage.ImageObjectStorage;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Handler reativo (WebFlux funcional) do {@code GET /images}: lista os resumos dos metadados das
 * imagens geradas ({@link ImageGenerationSummary}), na ordem devolvida pelo repositorio (mais
 * recentes primeiro). A consulta bloqueante roda no {@code boundedElastic} para nao travar o loop
 * reativo.
 */
@Component
public class ImageHistoryHandler {

  private final GeneratedImageRepository repository;
  private final ImageObjectStorage storage;

  /**
   * Construtor.
   *
   * @param repository repositorio JPA dos metadados das imagens geradas
   * @param storage object storage das imagens (usado em ciclo posterior)
   */
  public ImageHistoryHandler(GeneratedImageRepository repository, ImageObjectStorage storage) {
    this.repository = repository;
    this.storage = storage;
  }

  /**
   * Responde {@code 200} com o JSON array dos resumos.
   *
   * @param request requisicao (sem parametros)
   * @return JSON array snake_case em PT, na ordem do repositorio
   */
  public Mono<ServerResponse> list(ServerRequest request) {
    return Mono.fromCallable(repository::findAllByOrderByCriadoEmDesc)
        .subscribeOn(Schedulers.boundedElastic())
        .map(
            imagens ->
                imagens.stream()
                    .map(
                        imagem ->
                            new ImageGenerationSummary(
                                imagem.getId(),
                                imagem.getCriadoEm(),
                                imagem.getModelo(),
                                imagem.getCustoBrl(),
                                imagem.getLatenciaMs()))
                    .toList())
        .flatMap(
            resumos ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(resumos));
  }
}
