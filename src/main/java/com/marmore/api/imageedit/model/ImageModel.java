package com.marmore.api.imageedit.model;

import static com.marmore.api.imageedit.model.ImageQuality.HIGH;
import static com.marmore.api.imageedit.model.ImageQuality.LOW;
import static com.marmore.api.imageedit.model.ImageQuality.MEDIUM;
import static com.marmore.api.imageedit.model.ImageSize.LANDSCAPE;
import static com.marmore.api.imageedit.model.ImageSize.PORTRAIT;
import static com.marmore.api.imageedit.model.ImageSize.SQUARE;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Modelos do endpoint de edicao de imagem da OpenAI com seus precos oficiais (jul/2026) vinculados.
 * Cada modelo carrega sua tabela quality x size -> USD por imagem. {@link #GPT_IMAGE_1} suporta
 * input_fidelity porem nao e precificado neste servico, logo {@link #price} retorna {@link
 * Optional#empty()}.
 */
public enum ImageModel {
  GPT_IMAGE_2(
      "gpt-image-2",
      false,
      point(LOW, SQUARE, "0.006"),
      point(LOW, PORTRAIT, "0.005"),
      point(LOW, LANDSCAPE, "0.005"),
      point(MEDIUM, SQUARE, "0.053"),
      point(MEDIUM, PORTRAIT, "0.041"),
      point(MEDIUM, LANDSCAPE, "0.041"),
      point(HIGH, SQUARE, "0.211"),
      point(HIGH, PORTRAIT, "0.165"),
      point(HIGH, LANDSCAPE, "0.165")),
  GPT_IMAGE_1_5(
      "gpt-image-1.5",
      true,
      point(LOW, SQUARE, "0.009"),
      point(LOW, PORTRAIT, "0.013"),
      point(LOW, LANDSCAPE, "0.013"),
      point(MEDIUM, SQUARE, "0.034"),
      point(MEDIUM, PORTRAIT, "0.050"),
      point(MEDIUM, LANDSCAPE, "0.050"),
      point(HIGH, SQUARE, "0.133"),
      point(HIGH, PORTRAIT, "0.200"),
      point(HIGH, LANDSCAPE, "0.200")),
  GPT_IMAGE_1_MINI(
      "gpt-image-1-mini",
      true,
      point(LOW, SQUARE, "0.005"),
      point(LOW, PORTRAIT, "0.006"),
      point(LOW, LANDSCAPE, "0.006"),
      point(MEDIUM, SQUARE, "0.011"),
      point(MEDIUM, PORTRAIT, "0.015"),
      point(MEDIUM, LANDSCAPE, "0.015"),
      point(HIGH, SQUARE, "0.036"),
      point(HIGH, PORTRAIT, "0.052"),
      point(HIGH, LANDSCAPE, "0.052")),
  GPT_IMAGE_1("gpt-image-1", true);

  private final String apiValue;
  private final boolean supportsFidelity;
  private final List<PricePoint> points;

  ImageModel(String apiValue, boolean supportsFidelity, PricePoint... points) {
    this.apiValue = apiValue;
    this.supportsFidelity = supportsFidelity;
    this.points = List.of(points);
  }

  /** Valor literal enviado ao endpoint da OpenAI (ex.: {@code gpt-image-2}). */
  public String apiValue() {
    return apiValue;
  }

  /** Indica se o modelo aceita input_fidelity no endpoint de edicao. */
  public boolean supportsFidelity() {
    return supportsFidelity;
  }

  /**
   * Retorna o preco em USD por imagem para a combinacao quality/size, ou {@link Optional#empty()}
   * se o modelo nao tiver preco para ela.
   */
  public Optional<BigDecimal> price(ImageQuality quality, ImageSize size) {
    return points.stream()
        .filter(p -> p.quality == quality && p.size == size)
        .findFirst()
        .map(p -> p.usd);
  }

  /** Busca o modelo pelo valor do endpoint, ou {@link Optional#empty()} se for desconhecido. */
  public static Optional<ImageModel> fromApiValue(String value) {
    return Arrays.stream(values()).filter(m -> m.apiValue.equals(value)).findFirst();
  }

  private record PricePoint(ImageQuality quality, ImageSize size, BigDecimal usd) {
    PricePoint(ImageQuality quality, ImageSize size, String usd) {
      this(quality, size, new BigDecimal(usd));
    }
  }

  private static PricePoint point(ImageQuality quality, ImageSize size, String usd) {
    return new PricePoint(quality, size, usd);
  }
}
