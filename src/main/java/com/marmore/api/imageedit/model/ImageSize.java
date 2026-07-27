package com.marmore.api.imageedit.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Dimensoes aceitas pelo endpoint de edicao de imagem da OpenAI. {@link #AUTO} nao tem preco:
 * resolve para {@link #SQUARE} (default do produto) via {@link #resolve()}.
 */
public enum ImageSize {
  SQUARE("1024x1024"),
  PORTRAIT("1024x1536"),
  LANDSCAPE("1536x1024"),
  AUTO("auto");

  private final String apiValue;

  ImageSize(String apiValue) {
    this.apiValue = apiValue;
  }

  /** Valor literal enviado ao endpoint da OpenAI (ex.: {@code 1024x1024}). */
  public String apiValue() {
    return apiValue;
  }

  /** AUTO nao tem preco: resolve para o default do produto (SQUARE). */
  public ImageSize resolve() {
    return this == AUTO ? SQUARE : this;
  }

  /** Busca a dimensao pelo valor do endpoint, ou {@link Optional#empty()} se for desconhecida. */
  public static Optional<ImageSize> fromApiValue(String value) {
    return Arrays.stream(values()).filter(s -> s.apiValue.equals(value)).findFirst();
  }
}
