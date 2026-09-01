package com.marmore.api.imageedit.web;

import com.marmore.api.imageedit.domain.CatalogoPedras;
import com.marmore.api.imageedit.domain.Pedra;
import com.marmore.api.imageedit.domain.Produto;
import com.marmore.api.imageedit.storage.GeneratedImageRepository;
import com.marmore.api.imageedit.storage.ImageObjectStorage;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
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
  private final CatalogoPedras catalogo;

  /**
   * Construtor.
   *
   * @param repository repositorio JPA dos metadados das imagens geradas
   * @param storage object storage das imagens (usado em ciclo posterior)
   * @param catalogo catalogo de pedras para resolver o nome comercial
   */
  public ImageHistoryHandler(
      GeneratedImageRepository repository, ImageObjectStorage storage, CatalogoPedras catalogo) {
    this.repository = repository;
    this.storage = storage;
    this.catalogo = catalogo;
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
                                imagem.getLatenciaMs(),
                                imagem.getPedra(),
                                nomePedra(imagem.getPedra()),
                                imagem.getProduto(),
                                nomeProduto(imagem.getProduto())))
                    .toList())
        .flatMap(
            resumos ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(resumos));
  }

  /**
   * Resolve o nome comercial da pedra do catalogo a partir do id gravado nos metadados. Id nulo nao
   * consulta o catalogo.
   *
   * @param id id da pedra gravado na imagem (pode ser nulo ou desconhecido)
   * @return nome comercial do catalogo, ou nulo quando o id e nulo ou nao encontrado
   */
  private String nomePedra(String id) {
    if (id == null) {
      return null;
    }
    return catalogo.porId(id).map(Pedra::nome).orElse(null);
  }

  /**
   * Resolve o nome de exibicao do produto do catalogo a partir do id gravado nos metadados.
   *
   * @param id id do produto gravado na imagem (pode ser nulo ou desconhecido)
   * @return nome de exibicao do catalogo, ou nulo quando o id e nulo ou nao reconhecido
   */
  private static String nomeProduto(String id) {
    return Arrays.stream(Produto.values())
        .filter(produto -> produto.id().equals(id))
        .findFirst()
        .map(Produto::nomeExibicao)
        .orElse(null);
  }

  /**
   * Responde {@code 200 image/png} com os bytes da imagem baixados do object storage. Id malformado
   * ou inexistente no repositorio responde {@code 404}.
   *
   * @param request requisicao com {@code id} na path
   * @return bytes PNG do storage, ou 404
   */
  public Mono<ServerResponse> arquivo(ServerRequest request) {
    UUID id;
    try {
      id = UUID.fromString(request.pathVariable("id"));
    } catch (IllegalArgumentException e) {
      return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    return Mono.fromCallable(() -> repository.findById(id))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            entidade ->
                entidade
                    .map(
                        imagem ->
                            storage
                                .baixar(imagem.getObjetoKey())
                                .flatMap(
                                    bytes ->
                                        ServerResponse.ok()
                                            .contentType(MediaType.IMAGE_PNG)
                                            .bodyValue(bytes)))
                    .orElseGet(
                        () -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))));
  }
}
