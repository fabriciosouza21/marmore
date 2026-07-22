package com.marmore.api.image.ai;

/**
 * Imagem gerada por um modelo de IA, em base64. Anloga a {@code org.springframework.ai.image.Image}
 * (que carrega url ou b64Json), porem restrita ao formato b64 usado pelo produto. Imutavel
 * (record).
 *
 * @param b64Json imagem codificada em base64
 */
public record Image(String b64Json) {

  /** Factory estatica (Item 1, Effective Java). */
  public static Image of(String b64Json) {
    return new Image(b64Json);
  }
}
