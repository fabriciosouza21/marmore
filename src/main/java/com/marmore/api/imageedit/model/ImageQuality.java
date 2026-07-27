package com.marmore.api.imageedit.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Qualidades aceitas pelo endpoint de edicao de imagem da OpenAI. {@link #AUTO} nao tem preco:
 * resolve para {@link #MEDIUM} (default do produto) via {@link #resolve()}.
 */
public enum ImageQuality {
  LOW("low"),
  MEDIUM("medium"),
  HIGH("high"),
  AUTO("auto");

  private final String apiValue;

  ImageQuality(String apiValue) {
    this.apiValue = apiValue;
  }

  /** Valor literal enviado ao endpoint da OpenAI (ex.: {@code low}). */
  public String apiValue() {
    return apiValue;
  }

  /** AUTO nao tem preco: resolve para o default do produto (MEDIUM). */
  public ImageQuality resolve() {
    return this == AUTO ? MEDIUM : this;
  }

  /** Busca a qualidade pelo valor do endpoint, ou {@link Optional#empty()} se for desconhecida. */
  public static Optional<ImageQuality> fromApiValue(String value) {
    return Arrays.stream(values()).filter(q -> q.apiValue.equals(value)).findFirst();
  }
}
