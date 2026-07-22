package com.marmore.api.image.ai;

import org.springframework.lang.Nullable;

/**
 * Mensagem textual de um prompt de imagem. Espelha {@code
 * org.springframework.ai.image.ImageMessage}: apenas texto (e peso opcional). Nao carrega dados
 * binarios de imagem; para isso usamos {@link InputImage}.
 *
 * @param text conteudo textual do prompt
 * @param weight peso opcional da mensagem
 */
public record ImageMessage(String text, @Nullable Float weight) {

  /** Factory estatica para mensagem sem peso (Item 1, Effective Java). */
  public static ImageMessage of(String text) {
    return new ImageMessage(text, null);
  }
}
