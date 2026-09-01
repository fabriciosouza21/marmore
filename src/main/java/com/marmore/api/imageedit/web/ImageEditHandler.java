package com.marmore.api.imageedit.web;

import com.marmore.api.imageedit.domain.GenerateResult;
import com.marmore.api.imageedit.service.ImageEditService;
import java.time.Duration;
import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Handler reativo (WebFlux funcional) para {@code POST /images/edit}. Monta o fluxo de eventos SSE
 * completo do fluxo de edicao de imagem:
 *
 * <ul>
 *   <li>{@code status {fase:"recebido"}} imediatamente
 *   <li>{@code status {fase:"redimensionando"}} antes do resize
 *   <li>{@code status {fase:"gerando"}} antes da chamada ao gateway
 *   <li>{@code ping} a cada 15s enquanto o gateway processa (heartbeat), cancelado quando o
 *       resultado chega
 *   <li>{@code done} + {@code imagem} (em sucesso) OU {@code error} (em falha de dominio), sempre
 *       como par terminal
 * </ul>
 *
 * <p><strong>Composicao do heartbeat.</strong> O desafio reativo e fundir um {@code
 * Flux.interval(15s)} (heartbeat) com o {@code Mono} do service (resultado), de modo que o
 * heartbeat PARE assim que o resultado chega (nenhum ping apos {@code done}/{@code imagem}/{@code
 * error}).
 *
 * <p>Abordagem escolhida: cacheia o {@code Mono} do service com {@link Mono#cache()} (uma unica
 * subscription compartilhada entre o sinal de parada e o ramo que vira eventos) e usa:
 *
 * <pre>{@code
 * Flux.concat(
 *     statusEvents,
 *     Flux.merge(
 *         Flux.interval(HEARTBEAT).map(i -> events.ping())
 *              .takeUntilOther(cached),
 *         cached.flatMapMany(this::toResultEvents)))
 * }</pre>
 *
 * <ul>
 *   <li>{@code takeUntilOther(cached)} cancela o heartbeat no exato instante em que o resultado e
 *       emitido, garantindo nenhum ping apos o resultado.
 *   <li>{@link Flux#merge} corre em paralelo os dois ramos; o ramo resultado termina apos
 *       done+imagem/error, encerrando o merge.
 *   <li>{@link Flux#concat} serializa: status x3 SEMPRE primeiro, depois o merge heartbeat+
 *       resultado.
 *   <li>{@code cache()} e essencial: sem ele, {@code takeUntilOther} e {@code flatMapMany}
 *       subscreveriam o service duas vezes (rodando a geracao duas vezes).
 * </ul>
 *
 * <p><strong>Leitura do FilePart.</strong> O {@code FilePart} "image" e lido de forma reativa via
 * {@link DataBufferUtils#join} (concatena os buffers do Flux num so) e convertido a {@code byte[]}.
 * O buffer e liberado dentro de {@code readBytes} (no {@code finally}) para evitar leak em qualquer
 * caminho (sucesso, erro, cancelamento).
 *
 * <p>O campo de formulario "pedra" e obrigatorio: ausente ou em branco (so espacos) responde {@code
 * 400 BAD_REQUEST} antes de abrir o stream (sem acionar o service/gateway). Se a parte "image"
 * estiver ausente, responde {@code 400 BAD_REQUEST}.
 */
@Component
public class ImageEditHandler {

  /** Periodo do heartbeat (ping a cada 15s), conforme especificacao. */
  static final Duration HEARTBEAT = Duration.ofSeconds(15);

  private final ImageEditService service;
  private final SseEvents events;

  /**
   * Construtor.
   *
   * @param service servico reativo de edicao de imagem
   * @param events construtor de eventos SSE (serializacao JSON via Jackson)
   */
  public ImageEditHandler(ImageEditService service, SseEvents events) {
    this.service = service;
    this.events = events;
  }

  /**
   * Ponto de entrada do endpoint. Valida o campo "pedra" do multipart, le o FilePart "image",
   * converte para bytes (liberando o buffer) e devolve {@code 200 text/event-stream} cujo body e o
   * fluxo SSE gerado por {@link #stream(byte[])}.
   *
   * @param request pedido HTTP reativo
   * @return {@code Mono} com a resposta, {@code 400} se "pedra" estiver ausente/em branco ou se a
   *     parte "image" estiver ausente
   */
  public Mono<ServerResponse> edit(ServerRequest request) {
    return request
        .multipartData()
        .flatMap(
            multipart -> {
              if (!pedraValida(multipart)) {
                return badRequest("parte 'pedra' ausente ou em branco no multipart");
              }
              return readImagePart(multipart)
                  .flatMap(bytes -> buildResponse(stream(bytes)))
                  .switchIfEmpty(
                      Mono.defer(() -> badRequest("parte 'image' ausente no multipart")));
            });
  }

  /**
   * Verifica a obrigatoriedade do campo de formulario "pedra": precisa chegar como texto ({@link
   * FormFieldPart}) e nao pode ficar em branco apos o trim.
   *
   * @param multipart dados multipart do pedido
   * @return {@code true} se "pedra" estiver presente e preenchida
   */
  private static boolean pedraValida(MultiValueMap<String, Part> multipart) {
    Part part = multipart.getFirst("pedra");
    if (!(part instanceof FormFieldPart campo)) {
      return false;
    }
    return !campo.value().trim().isEmpty();
  }

  /**
   * Fluxo puro de eventos SSE para os bytes dados. Package-private para teste direto com {@link
   * reactor.test.StepVerifier#withVirtualTime}, sem o custo/complexidade de subir o servidor HTTP.
   *
   * @param bytes bytes da foto do ambiente a editar
   * @return fluxo de eventos SSE conforme a sequencia do PlantUML
   */
  Flux<ServerSentEvent<Object>> stream(byte[] bytes) {
    Mono<GenerateResult> cached = service.generate(bytes).cache();
    Flux<ServerSentEvent<Object>> statuses =
        Flux.just(
            events.status("recebido"), events.status("redimensionando"), events.status("gerando"));
    Flux<ServerSentEvent<Object>> heartbeat =
        Flux.interval(HEARTBEAT).map(i -> events.ping()).takeUntilOther(cached);
    Flux<ServerSentEvent<Object>> result = cached.flatMapMany(this::toResultEvents);
    return Flux.concat(statuses, Flux.merge(heartbeat, result));
  }

  /**
   * Traduz o resultado (Ok -> done+imagem; Err -> error) nos eventos SSE finais.
   *
   * @param result resultado do service (never throws)
   * @return fluxo com 1 evento (error) ou 2 (done, imagem)
   */
  private Flux<ServerSentEvent<Object>> toResultEvents(GenerateResult result) {
    if (result instanceof GenerateResult.Ok ok) {
      return Flux.just(
          events.done(ok.latencyMs(), ok.custoBrl().orElse(null), ok.usage()),
          events.imagem(ok.b64()));
    }
    GenerateResult.Err err = (GenerateResult.Err) result;
    return Flux.just(events.error(err.error(), err.latencyMs()));
  }

  /**
   * Le o {@code FilePart} "image" do multipart para {@code byte[]}, liberando o buffer. Devolve
   * {@link Mono#empty()} se a parte estiver ausente (sinalizando {@code 400} ao chamador).
   *
   * @param multipart dados multipart do pedido
   * @return {@code Mono} com os bytes, ou empty se "image" ausente
   */
  private Mono<byte[]> readImagePart(MultiValueMap<String, Part> multipart) {
    Part part = multipart.getFirst("image");
    if (!(part instanceof FilePart filePart)) {
      return Mono.empty();
    }
    return DataBufferUtils.join(filePart.content()).map(ImageEditHandler::readBytes);
  }

  /** Copia o conteudo do buffer para {@code byte[]} e libera o buffer no {@code finally}. */
  private static byte[] readBytes(DataBuffer buffer) {
    try {
      byte[] bytes = new byte[buffer.readableByteCount()];
      buffer.read(bytes);
      return bytes;
    } finally {
      DataBufferUtils.release(buffer);
    }
  }

  /** Constroi a resposta HTTP {@code 200 text/event-stream} cujo body e o fluxo SSE. */
  private static Mono<ServerResponse> buildResponse(Flux<ServerSentEvent<Object>> sse) {
    return ServerResponse.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .body(sse, ServerSentEvent.class);
  }

  /** Resposta {@code 400 BAD_REQUEST} com mensagem de validacao estavel. */
  private static Mono<ServerResponse> badRequest(String mensagem) {
    return ServerResponse.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("error", mensagem));
  }
}
