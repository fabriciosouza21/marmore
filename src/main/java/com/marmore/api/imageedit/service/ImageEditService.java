package com.marmore.api.imageedit.service;

import com.marmore.api.imageedit.ai.AiImageOptions;
import com.marmore.api.imageedit.ai.ImageEditModel;
import com.marmore.api.imageedit.ai.ImageEditPrompt;
import com.marmore.api.imageedit.ai.ImageResponse;
import com.marmore.api.imageedit.ai.InputImage;
import com.marmore.api.imageedit.config.ImageEditProperties;
import com.marmore.api.imageedit.cost.ImageCostCalculator;
import com.marmore.api.imageedit.cost.UsdBrlProvider;
import com.marmore.api.imageedit.domain.EditPrompts;
import com.marmore.api.imageedit.domain.GenerateResult;
import com.marmore.api.imageedit.domain.ImageCost;
import com.marmore.api.imageedit.storage.GeneratedImage;
import com.marmore.api.imageedit.storage.GeneratedImageRepository;
import com.marmore.api.imageedit.storage.ImageObjectStorage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Servico reativo de edicao de imagem. Orquestra as validacoes pre-rede (api-key, pedra em disco,
 * resize do ambiente), monta o {@link ImageEditPrompt}, delega a chamada ao {@link ImageEditModel}
 * (gateway reativo) e computa o custo em BRL. Atua como tradutor entre o contrato do gateway (que
 * propaga erros via {@code Mono#error}) e o contrato interno {@link GenerateResult} (Ok/Err, nunca
 * lanca): nenhum caminho lanca excecao, todas as falhas viram {@link GenerateResult.Err}.
 *
 * <p>Composicao reativa:
 *
 * <ul>
 *   <li>Validacoes sincronas (api-key, arquivo da pedra em disco) rodam em {@link Mono#defer} na
 *       subscricao. Falha -> {@link GenerateResult.Err}.
 *   <li>Resize (bloqueante, AWT/Thumbnailator) e leitura dos bytes da pedra (I/O de disco) rodam em
 *       {@link Mono#fromCallable} sobre {@link Schedulers#boundedElastic()} para nao prender o
 *       event loop. Falha de decode -> {@link GenerateResult.Err}.
 *   <li>A chamada ao gateway ({@link ImageEditModel#call}) ja e reativa. {@code latencyMs} mede
 *       apenas essa chamada (do envio do request ate a {@link ImageResponse}).
 *   <li>Custo: {@link ImageCostCalculator#costUsd} x {@link UsdBrlProvider#currentRate}. Falha da
 *       cotacao nao derruba a geracao: o {@link GenerateResult.Ok} e devolvido com custo nulo.
 *   <li>Persistencia (caminho feliz): os bytes da imagem sao gravados no object storage ({@link
 *       ImageObjectStorage}) e os metadados no repositorio JPA; o {@link GenerateResult.Ok}
 *       devolvido e o mesmo da geracao.
 * </ul>
 */
@Service
public class ImageEditService {

  private static final Logger log = LoggerFactory.getLogger(ImageEditService.class);

  /** Mensagem generica para falhas de configuracao/gateway/upstream: nao vaza detalhe interno. */
  static final String MSG_FALHA_GERACAO = "falha na geracao da imagem";

  /** Mensagem para entrada indecodificavel: acionavel pelo cliente, sem detalhe interno. */
  static final String MSG_ENTRADA_INVALIDA = "imagem de entrada invalida ou ilegivel";

  private final ImageEditProperties props;
  private final ImageResizer resizer;
  private final ImageEditModel model;
  private final UsdBrlProvider usdBrl;
  private final ImageObjectStorage storage;
  private final GeneratedImageRepository repository;

  /**
   * Construtor.
   *
   * @param props propriedades do modulo (apiKey, stonePath, etc.)
   * @param resizer redimensionador de imagem (bloqueante)
   * @param model gateway de edicao de imagem reativo (OpenAI ou outro)
   * @param usdBrl provedor da cotacao USD->BRL (reativo, com cache/fallback)
   * @param storage armazenamento de objetos das imagens geradas (MinIO)
   * @param repository repositorio JPA dos metadados das imagens geradas
   */
  public ImageEditService(
      ImageEditProperties props,
      ImageResizer resizer,
      ImageEditModel model,
      UsdBrlProvider usdBrl,
      ImageObjectStorage storage,
      GeneratedImageRepository repository) {
    this.props = props;
    this.resizer = resizer;
    this.model = model;
    this.usdBrl = usdBrl;
    this.storage = storage;
    this.repository = repository;
  }

  /**
   * Gera/edita imagem a partir dos bytes do ambiente, injetando o prompt fixo e a imagem da pedra.
   *
   * <p>Contrato "never throws": qualquer excecao (validacao, I/O, gateway) vira {@link
   * GenerateResult.Err}. O detalhe da excecao e registrado em log (server-side); o cliente recebe
   * apenas mensagens genericas e estaveis.
   *
   * @param ambiente bytes da foto do ambiente a ser editada
   * @return {@link Mono} com sucesso (b64 + custo) ou erro; nunca lanca
   */
  public Mono<GenerateResult> generate(byte[] ambiente) {
    long[] inicio = {0L};
    return Mono.defer(
            () -> {
              inicio[0] = System.nanoTime();
              Optional<GenerateResult.Err> validacao = validate();
              if (validacao.isPresent()) {
                return Mono.just(validacao.get());
              }
              return prepareInputs(ambiente).flatMap(inputs -> callGateway(inputs, inicio[0]));
            })
        .onErrorResume(error -> Mono.just(toErr(error, inicio[0])));
  }

  /** Validacoes sincronas (api-key presente, pedra em disco). Retorna Err generico ou empty. */
  private Optional<GenerateResult.Err> validate() {
    String apiKey = props.getApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("OPENAI_API_KEY ausente no ambiente");
      return Optional.of(new GenerateResult.Err(MSG_FALHA_GERACAO, 0L));
    }
    if (props.getStonePath() == null || !Files.exists(props.getStonePath())) {
      log.warn("stone-path ausente ou inexistente: {}", props.getStonePath());
      return Optional.of(new GenerateResult.Err(MSG_FALHA_GERACAO, 0L));
    }
    return Optional.empty();
  }

  /**
   * Resize do ambiente + leitura dos bytes da pedra, ambos bloqueantes, em boundedElastic. Falha de
   * decode vira Err.
   */
  private Mono<PreparedInputs> prepareInputs(byte[] ambiente) {
    return Mono.fromCallable(
            () -> {
              Optional<byte[]> ambienteReduzido = resizer.resize(ambiente);
              if (ambienteReduzido.isEmpty()) {
                throw new DecodeFailedException();
              }
              byte[] pedraBytes = readStoneBytes();
              return new PreparedInputs(ambienteReduzido.get(), pedraBytes);
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  /** Le os bytes da pedra do disco. {@link Files#exists} ja validado em {@link #validate}. */
  private byte[] readStoneBytes() {
    try {
      return Files.readAllBytes(props.getStonePath());
    } catch (IOException e) {
      throw new StoneReadException(e);
    }
  }

  /** Monta o prompt, chama o gateway (medindo latencia), extrai b64 e computa custo. */
  private Mono<GenerateResult> callGateway(PreparedInputs inputs, long pipelineStart) {
    ImageEditPrompt prompt =
        ImageEditPrompt.of(
            EditPrompts.COUNTERTOP,
            AiImageOptions.defaults(),
            List.of(
                InputImage.of(inputs.ambiente(), "ambiente.jpg"),
                InputImage.of(inputs.pedra(), nomeDoArquivoDaPedra())));
    final long callStart = System.nanoTime();
    return model.call(prompt).flatMap(resp -> toResult(resp, callStart));
  }

  /**
   * Extrai b64 da resposta e monta o resultado. Sem b64 -> Err generico. Caso contrario computa o
   * custo de forma reativa: falha da cotacao nao derruba a geracao (Ok com custo nulo). No caminho
   * feliz persiste a imagem e devolve o mesmo {@link GenerateResult.Ok}.
   */
  private Mono<GenerateResult> toResult(ImageResponse resp, long callStart) {
    long latency = ms(callStart);
    Optional<String> b64 = resp.firstB64();
    if (b64.isEmpty()) {
      log.warn("resposta do gateway sem image_b64/b64_json");
      return Mono.just(new GenerateResult.Err(MSG_FALHA_GERACAO, latency));
    }
    String b64Value = b64.get();
    java.util.function.Function<ImageCost, GenerateResult> toOkWithCost =
        cost -> new GenerateResult.Ok(b64Value, resp.metadata().usage(), latency, cost);
    GenerateResult okSemCusto =
        new GenerateResult.Ok(b64Value, resp.metadata().usage(), latency, null);
    return computeCost()
        .map(toOkWithCost)
        .onErrorResume(error -> Mono.just(okSemCusto))
        .switchIfEmpty(Mono.just(okSemCusto))
        .flatMap(this::persistirImagem);
  }

  /**
   * Persiste a imagem gerada com sucesso: decodifica o b64, envia os bytes ao object storage e
   * grava os metadados no repositorio (JPA, bloqueante, em boundedElastic). Ordem: bytes primeiro
   * (a key vem do storage), linha depois. Devolve o MESMO resultado recebido, sem reconstruir o Ok.
   */
  private Mono<GenerateResult> persistirImagem(GenerateResult resultado) {
    if (!(resultado instanceof GenerateResult.Ok ok)) {
      return Mono.just(resultado);
    }
    return storage
        .salvar(Base64.getDecoder().decode(ok.b64()))
        .flatMap(
            objetoKey ->
                Mono.fromCallable(() -> repository.save(novaImagem(ok, objetoKey)))
                    .subscribeOn(Schedulers.boundedElastic()))
        .thenReturn(resultado);
  }

  /** Monta os metadados da imagem gerada a partir do resultado e da key devolvida pelo storage. */
  private GeneratedImage novaImagem(GenerateResult.Ok ok, String objetoKey) {
    GeneratedImage imagem = new GeneratedImage();
    imagem.setCriadoEm(Instant.now());
    imagem.setLatenciaMs(ok.latencyMs());
    imagem.setCustoBrl(ok.cost() == null ? null : ok.cost().costBrl());
    imagem.setModelo(props.getDefaultModel());
    imagem.setObjetoKey(objetoKey);
    return imagem;
  }

  /**
   * Computa o custo em BRL: {@code calculator.costUsd(model, quality, size)} x cotacao atual.
   * Deriva model/quality/size de {@link AiImageOptions#defaults()} (contrato do produto). Retorna
   * empty se a combinacao nao existir na tabela; propaga falha da cotacao via onError.
   */
  private Mono<ImageCost> computeCost() {
    AiImageOptions opts = AiImageOptions.defaults();
    Optional<BigDecimal> costUsd = ImageCostCalculator.costUsd(opts);
    if (costUsd.isEmpty()) {
      return Mono.empty();
    }
    final BigDecimal usd = costUsd.get();
    return usdBrl.currentRate().map(rate -> new ImageCost(usd, usd.multiply(rate)));
  }

  /** Extrai o nome do arquivo da pedra do path configurado (fallback "pedra.png"). */
  private String nomeDoArquivoDaPedra() {
    var fileName = props.getStonePath().getFileName();
    return fileName != null ? fileName.toString() : "pedra.png";
  }

  /**
   * Traduz qualquer excecao em {@link GenerateResult.Err} com mensagem generica. O detalhe real
   * fica apenas no log (server-side). Decode leva a mensagem de entrada invalida; demais a geral.
   */
  private static GenerateResult.Err toErr(Throwable error, long start) {
    long latency = ms(start);
    if (error instanceof DecodeFailedException) {
      return new GenerateResult.Err(MSG_ENTRADA_INVALIDA, latency);
    }
    log.warn("geracao de imagem falhou", error);
    return new GenerateResult.Err(MSG_FALHA_GERACAO, latency);
  }

  private static long ms(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }

  /** Bytes ja preparados (ambiente redimensionado + pedra lida do disco). */
  private record PreparedInputs(byte[] ambiente, byte[] pedra) {

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof PreparedInputs(byte[] ambiente1, byte[] pedra1))) {
        return false;
      }
      return Arrays.equals(ambiente, ambiente1) && Arrays.equals(pedra, pedra1);
    }

    @Override
    public int hashCode() {
      int result = Arrays.hashCode(ambiente);
      return 31 * result + Arrays.hashCode(pedra);
    }

    @Override
    public String toString() {
      return "PreparedInputs{ambiente=<%d bytes>, pedra=<%d bytes>}"
          .formatted(ambiente.length, pedra.length);
    }
  }

  /** Sinal interno de falha de decode, traduzido para Err em {@link #toErr}. */
  private static final class DecodeFailedException extends RuntimeException {
    DecodeFailedException() {
      super("unable to decode input image");
    }
  }

  /** Sinal interno de falha de leitura da pedra. */
  private static final class StoneReadException extends RuntimeException {
    StoneReadException(Throwable cause) {
      super("stone image not found: " + cause.getMessage(), cause);
    }
  }
}
