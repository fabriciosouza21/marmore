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
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
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
 * </ul>
 */
@Service
public class ImageEditService {

  private final ImageEditProperties props;
  private final ImageResizer resizer;
  private final ImageEditModel model;
  private final UsdBrlProvider usdBrl;

  /**
   * Construtor.
   *
   * @param props propriedades do modulo (apiKey, stonePath, etc.)
   * @param resizer redimensionador de imagem (bloqueante)
   * @param model gateway de edicao de imagem reativo (OpenAI ou outro)
   * @param usdBrl provedor da cotacao USD->BRL (reativo, com cache/fallback)
   */
  public ImageEditService(
      ImageEditProperties props,
      ImageResizer resizer,
      ImageEditModel model,
      UsdBrlProvider usdBrl) {
    this.props = props;
    this.resizer = resizer;
    this.model = model;
    this.usdBrl = usdBrl;
  }

  /**
   * Gera/edita imagem a partir dos bytes do ambiente, injetando o prompt fixo e a imagem da pedra.
   *
   * @param ambiente bytes da foto do ambiente a ser editada
   * @return {@link Mono} com sucesso (b64 + custo) ou erro; nunca lanca
   */
  public Mono<GenerateResult> generate(byte[] ambiente) {
    return Mono.defer(
        () -> {
          long pipelineStart = System.nanoTime();
          Optional<GenerateResult.Err> validation = validate(pipelineStart);
          if (validation.isPresent()) {
            return Mono.just(validation.get());
          }
          return prepareInputs(ambiente)
              .flatMap(this::callGateway)
              .onErrorResume(error -> Mono.just(toErr(error, pipelineStart)));
        });
  }

  /** Validacoes sincronas (api-key presente, pedra em disco). Retorna Err ou empty. */
  private Optional<GenerateResult.Err> validate(long start) {
    String apiKey = props.getApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      return Optional.of(
          new GenerateResult.Err("OPENAI_API_KEY ausente. Defina no ambiente.", ms(start)));
    }
    if (!Files.exists(props.getStonePath())) {
      return Optional.of(
          new GenerateResult.Err("stone image not found: " + props.getStonePath(), ms(start)));
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
  private Mono<GenerateResult> callGateway(PreparedInputs inputs) {
    long pipelineStart = System.nanoTime();
    ImageEditPrompt prompt =
        ImageEditPrompt.of(
            EditPrompts.COUNTERTOP,
            AiImageOptions.defaults(),
            List.of(
                InputImage.of(inputs.ambiente(), "ambiente.jpg"),
                InputImage.of(inputs.pedra(), nomeDoArquivoDaPedra())));
    final long callStart = System.nanoTime();
    return model
        .call(prompt)
        .flatMap(resp -> toResult(resp, callStart))
        .onErrorResume(error -> Mono.just(toErr(error, pipelineStart)));
  }

  /**
   * Extrai b64 da resposta e monta o resultado. Sem b64 -> Err. Caso contrario computa o custo de
   * forma reativa: falha da cotacao nao derruba a geracao (Ok com custo nulo).
   */
  private Mono<GenerateResult> toResult(ImageResponse resp, long callStart) {
    long latency = ms(callStart);
    Optional<String> b64 = resp.firstB64();
    if (b64.isEmpty()) {
      return Mono.just(new GenerateResult.Err("resposta sem b64_json", latency));
    }
    String b64Value = b64.get();
    java.util.function.Function<ImageCost, GenerateResult> toOkWithCost =
        cost -> new GenerateResult.Ok(b64Value, resp.raw(), resp.metadata().usage(), latency, cost);
    GenerateResult okSemCusto =
        new GenerateResult.Ok(b64Value, resp.raw(), resp.metadata().usage(), latency, null);
    return computeCost()
        .map(toOkWithCost)
        .onErrorResume(error -> Mono.just(okSemCusto))
        .switchIfEmpty(Mono.just(okSemCusto));
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
    return usdBrl.currentRate().map(rate -> new ImageCost(usd, usd.multiply(rate), null));
  }

  /** Extrai o nome do arquivo da pedra do path configurado (fallback "pedra.png"). */
  private String nomeDoArquivoDaPedra() {
    var fileName = props.getStonePath().getFileName();
    return fileName != null ? fileName.toString() : "pedra.png";
  }

  /** Traduz qualquer excecao em {@link GenerateResult.Err}. */
  private static GenerateResult.Err toErr(Throwable error, long start) {
    if (error instanceof DecodeFailedException) {
      return new GenerateResult.Err("unable to decode input image", ms(start));
    }
    String message = error.getMessage() != null ? error.getMessage() : error.toString();
    return new GenerateResult.Err(message, ms(start));
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
    @NotNull
    public String toString() {
      return "PreparedInputs{"
          + "ambiente="
          + Arrays.toString(ambiente)
          + ", pedra="
          + Arrays.toString(pedra)
          + '}';
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
