package com.marmore.api.imageedit.ai;

/**
 * Resultado de uma geracao individual de imagem. Anloga a {@code
 * org.springframework.ai.image.ImageGeneration}. Carrega apenas a {@link Image} de saida; metadados
 * por geracao serao adicionados quando houver demanda. Imutavel (record).
 *
 * @param output imagem gerada
 */
public record ImageGeneration(Image output) {

  /** Factory estatica (Item 1, Effective Java). */
  public static ImageGeneration of(Image output) {
    return new ImageGeneration(output);
  }
}
