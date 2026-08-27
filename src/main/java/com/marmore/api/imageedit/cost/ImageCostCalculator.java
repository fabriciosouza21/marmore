package com.marmore.api.imageedit.cost;

import com.marmore.api.imageedit.ai.AiImageOptions;
import com.marmore.api.imageedit.model.ImageModel;
import com.marmore.api.imageedit.model.ImageQuality;
import com.marmore.api.imageedit.model.ImageSize;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Calcula o custo em USD de uma geracao de imagem. Classe de metodos static (Item 4, Effective
 * Java): sem estado, sem injecao, sem necessidade de instanciacao.
 *
 * <p>Os precos e as regras de resolucao de {@code auto} vivem nos enums {@link ImageModel}, {@link
 * ImageQuality} e {@link ImageSize}. Esta classe apenas adapta as Strings de entrada vindas de
 * {@link AiImageOptions} para os enums e delega.
 */
public final class ImageCostCalculator {

  private ImageCostCalculator() {}

  /**
   * Retorna o custo em USD por imagem para a combinacao {@code model}/{@code quality}/{@code size},
   * ou {@link Optional#empty()} se a combinacao nao existir na tabela.
   *
   * @param model modelo (ex.: {@code gpt-image-2})
   * @param quality qualidade ({@code low}/{@code medium}/{@code high}/{@code auto})
   * @param size tamanho literal do endpoint ({@code 1024x1024}, etc., ou {@code auto})
   */
  public static Optional<BigDecimal> costUsd(String model, String quality, String size) {
    Optional<ImageModel> m = ImageModel.fromApiValue(model);
    Optional<ImageQuality> q = ImageQuality.fromApiValue(quality).map(ImageQuality::resolve);
    Optional<ImageSize> s = ImageSize.fromApiValue(size).map(ImageSize::resolve);
    if (m.isEmpty() || q.isEmpty() || s.isEmpty()) {
      return Optional.empty();
    }
    return m.get().price(q.get(), s.get());
  }

  /**
   * Retorna o custo em USD por imagem para as opcoes dadas, delegando para {@link #costUsd(String,
   * String, String)}.
   *
   * @param opts opcoes da chamada (model, quality, size)
   */
  public static Optional<BigDecimal> costUsd(AiImageOptions opts) {
    return costUsd(opts.model(), opts.quality(), opts.size());
  }
}
