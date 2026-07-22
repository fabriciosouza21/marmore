package com.marmore.api.image.ai;

import org.springframework.lang.Nullable;

/**
 * Imagem gerada por um modelo de IA. Espelha {@code org.springframework.ai.image.Image}: carrega ou
 * uma URL ou a imagem em base64. Imutavel (record), ao contrario do Spring AI onde {@code Image} e
 * mutavel. Preferimos imutabilidade (Item 17, Effective Java).
 *
 * @param url URL onde a imagem pode ser acessada
 * @param b64Json imagem codificada em base64
 */
public record Image(@Nullable String url, @Nullable String b64Json) {

  /** Factory estatica para imagem em base64 (Item 1, Effective Java). */
  public static Image ofB64(String b64Json) {
    return new Image(null, b64Json);
  }

  /** Factory estatica para imagem por URL (Item 1, Effective Java). */
  public static Image ofUrl(String url) {
    return new Image(url, null);
  }
}
