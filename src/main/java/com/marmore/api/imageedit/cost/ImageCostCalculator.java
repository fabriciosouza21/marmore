package com.marmore.api.imageedit.cost;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Calcula o custo em USD de uma geracao de imagem a partir de uma tabela hardcodeada indexada por
 * modelo, qualidade e tamanho. Stateless: pode ser instanciada direto ou usada como bean Spring.
 *
 * <p>A tabela reflete os precos oficiais OpenAI (jul/2026, mesma fonte da rinha). O valor e por
 * imagem (a OpenAI cobra por imagem, nao por token), entao o custo depende apenas de tres
 * parametros.
 *
 * <p>Regras de resolucao:
 *
 * <ul>
 *   <li>{@code "auto"} em qualidade resolve para {@code "medium"}.
 *   <li>{@code "auto"} em tamanho resolve para {@code "1024x1024"}.
 * </ul>
 *
 * <p>Combinacoes ausentes na tabela retornam {@link Optional#empty()}.
 */
public final class ImageCostCalculator {

  /** Default de tamanho quando {@code auto} ou ausente (default da OpenAI). */
  private static final String DEFAULT_SIZE = "1024x1024";

  /** Default de qualidade quando {@code auto} ou ausente (default da OpenAI). */
  private static final String DEFAULT_QUALITY = "medium";

  /**
   * Tabela imutavel de precos: model -> quality -> size -> USD por imagem. Preco oficiais OpenAI
   * jul/2026. Construida uma unica vez na carga da classe; nunca mutada.
   */
  private static final Map<String, Map<String, Map<String, BigDecimal>>> PRICE_PER_IMAGE =
      Map.of(
          "gpt-image-2",
          Map.of(
              "low",
              Map.of(
                  "1024x1024", new BigDecimal("0.006"),
                  "1024x1536", new BigDecimal("0.005"),
                  "1536x1024", new BigDecimal("0.005")),
              "medium",
              Map.of(
                  "1024x1024", new BigDecimal("0.053"),
                  "1024x1536", new BigDecimal("0.041"),
                  "1536x1024", new BigDecimal("0.041")),
              "high",
              Map.of(
                  "1024x1024", new BigDecimal("0.211"),
                  "1024x1536", new BigDecimal("0.165"),
                  "1536x1024", new BigDecimal("0.165"))),
          "gpt-image-1.5",
          Map.of(
              "low",
              Map.of(
                  "1024x1024", new BigDecimal("0.009"),
                  "1024x1536", new BigDecimal("0.013"),
                  "1536x1024", new BigDecimal("0.013")),
              "medium",
              Map.of(
                  "1024x1024", new BigDecimal("0.034"),
                  "1024x1536", new BigDecimal("0.050"),
                  "1536x1024", new BigDecimal("0.050")),
              "high",
              Map.of(
                  "1024x1024", new BigDecimal("0.133"),
                  "1024x1536", new BigDecimal("0.200"),
                  "1536x1024", new BigDecimal("0.200"))),
          "gpt-image-1-mini",
          Map.of(
              "low",
              Map.of(
                  "1024x1024", new BigDecimal("0.005"),
                  "1024x1536", new BigDecimal("0.006"),
                  "1536x1024", new BigDecimal("0.006")),
              "medium",
              Map.of(
                  "1024x1024", new BigDecimal("0.011"),
                  "1024x1536", new BigDecimal("0.015"),
                  "1536x1024", new BigDecimal("0.015")),
              "high",
              Map.of(
                  "1024x1024", new BigDecimal("0.036"),
                  "1024x1536", new BigDecimal("0.052"),
                  "1536x1024", new BigDecimal("0.052"))));

  /** Construtor sem args; a classe nao tem estado. */
  public ImageCostCalculator() {}

  /**
   * Retorna o custo em USD por imagem para a combinacao {@code model}/{@code quality}/{@code size},
   * ou {@link Optional#empty()} se a combinacao nao existir na tabela.
   *
   * @param model modelo (ex.: {@code gpt-image-2})
   * @param quality qualidade ({@code low}/{@code medium}/{@code high}/{@code auto})
   * @param size tamanho literal do endpoint ({@code 1024x1024}, etc., ou {@code auto})
   */
  public Optional<BigDecimal> costUsd(String model, String quality, String size) {
    String resolvedQuality = "auto".equals(quality) ? DEFAULT_QUALITY : quality;
    String resolvedSize = "auto".equals(size) ? DEFAULT_SIZE : size;
    return Optional.ofNullable(PRICE_PER_IMAGE.get(model))
        .map(m -> m.get(resolvedQuality))
        .map(m -> m.get(resolvedSize));
  }
}
