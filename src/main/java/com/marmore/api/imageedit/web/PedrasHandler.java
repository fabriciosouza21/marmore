package com.marmore.api.imageedit.web;

import com.marmore.api.imageedit.config.ImageEditProperties;
import com.marmore.api.imageedit.domain.CatalogoPedras;
import com.marmore.api.imageedit.domain.Pedra;
import java.nio.file.Files;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Handler reativo (WebFlux funcional) do catalogo de pedras: {@code GET /pedras} lista os resumos
 * ({@link PedraSummary}, sem o campo interno {@code arquivo}) na ordem do catalogo e {@code GET
 * /pedras/{id}/imagem} devolve os bytes da imagem do diretorio do catalogo com Content-Type
 * conforme a extensao. As leituras de disco bloqueantes rodam no {@code boundedElastic} para nao
 * travar o loop reativo. Id inexistente responde {@code 404}.
 */
@Component
public class PedrasHandler {

  private final CatalogoPedras catalogo;
  private final ImageEditProperties props;

  /**
   * Construtor.
   *
   * @param catalogo catalogo de pedras carregado na inicializacao
   * @param props propriedades do modulo (diretorio das imagens das pedras)
   */
  public PedrasHandler(CatalogoPedras catalogo, ImageEditProperties props) {
    this.catalogo = catalogo;
    this.props = props;
  }

  /**
   * Responde {@code 200} com o JSON array dos resumos, na ordem do catalogo.
   *
   * @param request requisicao (sem parametros)
   * @return JSON array de {@link PedraSummary}
   */
  public Mono<ServerResponse> list(ServerRequest request) {
    return Mono.fromCallable(catalogo::listar)
        .subscribeOn(Schedulers.boundedElastic())
        .map(
            pedras ->
                pedras.stream()
                    .map(pedra -> new PedraSummary(pedra.id(), pedra.nome(), pedra.categoria()))
                    .toList())
        .flatMap(
            resumos ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(resumos));
  }

  /**
   * Responde {@code 200} com os bytes da imagem da pedra localizada por id. Id inexistente responde
   * {@code 404}.
   *
   * @param request requisicao com {@code id} na path
   * @return bytes da imagem com Content-Type pela extensao, ou 404
   */
  public Mono<ServerResponse> imagem(ServerRequest request) {
    return Mono.fromCallable(() -> catalogo.porId(request.pathVariable("id")))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            talvez ->
                talvez
                    .map(this::responderImagem)
                    .orElseGet(
                        () -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))));
  }

  /** Le os bytes da imagem no diretorio do catalogo e responde {@code 200} com o Content-Type. */
  private Mono<ServerResponse> responderImagem(Pedra pedra) {
    return Mono.fromCallable(
            () -> Files.readAllBytes(props.getPedrasPath().resolve(pedra.arquivo())))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            bytes ->
                ServerResponse.ok().contentType(contentTypeDe(pedra.arquivo())).bodyValue(bytes));
  }

  /** Resolve o Content-Type pela extensao do arquivo declarado no catalogo. */
  private static MediaType contentTypeDe(String arquivo) {
    if (arquivo.endsWith(".png")) {
      return MediaType.IMAGE_PNG;
    }
    if (arquivo.endsWith(".jpg") || arquivo.endsWith(".jpeg")) {
      return MediaType.IMAGE_JPEG;
    }
    return MediaType.APPLICATION_OCTET_STREAM;
  }
}
