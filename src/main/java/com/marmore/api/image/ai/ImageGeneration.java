package com.marmore.api.image.ai;

/**
 * Resultado de uma geracao individual de imagem. Espelha {@code
 * org.springframework.ai.image.ImageGeneration}: junta a {@link Image} de saida com seus metadados.
 * Imutavel (record).
 *
 * @param output imagem gerada
 * @param metadata metadados da geracao
 */
public record ImageGeneration(Image output, ImageGenerationMetadata metadata) {

  /** Factory estatica para geracao sem metadata (Item 1, Effective Java). */
  public static ImageGeneration of(Image output) {
    return new ImageGeneration(output, new ImageGenerationMetadata());
  }
}
