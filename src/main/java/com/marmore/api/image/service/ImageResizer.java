package com.marmore.api.image.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

/**
 * Redimensiona e re-codifica imagens em memoria. Entrada invalida vira {@link Optional#empty()},
 * nunca lanca. Saida e sempre JPEG qualidade 0.85.
 */
@Component
public class ImageResizer {

  private static final int MAX_LADO = 1536;
  private static final double QUALIDADE = 0.85;

  /**
   * Redimensiona a imagem de entrada para no maximo {@value MAX_LADO} no maior lado, mantendo
   * aspecto. Nao faz upscaling. Re-codifica como JPEG qualidade {@value QUALIDADE}.
   *
   * @param input bytes da imagem original (PNG, JPEG, etc.)
   * @return imagem redimensionada, ou empty se a entrada nao for decodificavel
   */
  public Optional<byte[]> resize(byte[] input) {
    if (input == null || input.length == 0) {
      return Optional.empty();
    }
    BufferedImage original;
    try {
      original = ImageIO.read(new ByteArrayInputStream(input));
    } catch (IOException e) {
      return Optional.empty();
    }
    if (original == null) {
      return Optional.empty();
    }
    int largura = original.getWidth();
    int altura = original.getHeight();
    int maiorLado = Math.max(largura, altura);
    int alvo = Math.min(maiorLado, MAX_LADO);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      Thumbnails.of(original)
          .size(alvo, alvo)
          .outputFormat("jpg")
          .outputQuality(QUALIDADE)
          .toOutputStream(out);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Optional.of(out.toByteArray());
  }
}
