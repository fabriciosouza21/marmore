package com.marmore.api.image.ai;

/**
 * Falha irrecuperavel na chamada a um modelo de imagem. Unchecked (Item 71, Effective Java): o
 * chamador ({@code ImageEditService}) traduz para {@code GenerateResult.Err}. Sem acoplamento com
 * HTTP — a camada web decide o status.
 */
public class AiImageException extends RuntimeException {

  /**
   * Construtor.
   *
   * @param message mensagem de erro
   */
  public AiImageException(String message) {
    super(message);
  }

  /**
   * Construtor com causa.
   *
   * @param message mensagem de erro
   * @param cause causa original
   */
  public AiImageException(String message, Throwable cause) {
    super(message, cause);
  }
}
