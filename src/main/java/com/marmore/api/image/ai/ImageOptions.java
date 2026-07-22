package com.marmore.api.image.ai;

import org.springframework.lang.Nullable;

/**
 * Opcoes de uma chamada de geracao/edicao de imagem. Espelha {@code
 * org.springframework.ai.image.ImageOptions}: apenas getters anulaveis, sem setters. A impl
 * concreta e um record imutavel.
 */
public interface ImageOptions {

  /** Numero de imagens a gerar. */
  @Nullable
  Integer getN();

  /** Modelo de geracao (ex.: {@code gpt-image-2}). */
  @Nullable
  String getModel();

  /** Largura em pixels. */
  @Nullable
  Integer getWidth();

  /** Altura em pixels. */
  @Nullable
  Integer getHeight();

  /** Formato da resposta ({@code url} ou {@code b64_json}). */
  @Nullable
  String getResponseFormat();

  /** Estilo da imagem. */
  @Nullable
  String getStyle();
}
