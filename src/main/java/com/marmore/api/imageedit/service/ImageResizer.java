package com.marmore.api.imageedit.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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
   * Dimensao maxima aceita por lado (largura ou altura). Imagens acima disso sao rejeitadas antes
   * da decodificacao do raster, evitando que uma "bomba de descompressao" (imagem compacta cujo
   * raster decodificado ocupa centenas de MB) esgote a heap. Limita o pico de memoria por imagem a
   * {@code MAX_DIM x MAX_DIM x 4 bytes} (~67 MB).
   */
  static final int MAX_DIM = 4096;

  /**
   * Redimensiona a imagem de entrada para no maximo {@value MAX_LADO} no maior lado, mantendo
   * aspecto. Nao faz upscaling. Re-codifica como JPEG qualidade {@value QUALIDADE}.
   *
   * <p>As dimensoes sao lidas do cabecalho via {@link ImageReader#getWidth(int)} / {@link
   * ImageReader#getHeight(int)} <strong>antes</strong> de decodificar o raster. Imagens cuja
   * largura ou altura excedam {@link #MAX_DIM} sao rejeitadas como {@link Optional#empty()}.
   *
   * @param input bytes da imagem original (PNG, JPEG, etc.)
   * @return imagem redimensionada, ou empty se a entrada nao for decodificavel ou exceder o limite
   */
  public Optional<byte[]> resize(byte[] input) {
    if (input == null || input.length == 0) {
      return Optional.empty();
    }
    BufferedImage original;
    try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
      if (iis == null) {
        return Optional.empty();
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
      if (!readers.hasNext()) {
        return Optional.empty();
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(iis);
        int largura = reader.getWidth(0);
        int altura = reader.getHeight(0);
        if (largura > MAX_DIM || altura > MAX_DIM) {
          return Optional.empty();
        }
        original = reader.read(0);
      } finally {
        reader.dispose();
      }
    } catch (IOException ignored) {
      return Optional.empty();
    }
    if (original == null) {
      return Optional.empty();
    }
    int largura = original.getWidth();
    int altura = original.getHeight();
    int maiorLado = Math.max(largura, altura);
    int alvo = Math.min(maiorLado, MAX_LADO);
    try (var out = new ByteArrayOutputStream()) {
      Thumbnails.of(original)
          .size(alvo, alvo)
          .outputFormat("jpg")
          .outputQuality(QUALIDADE)
          .toOutputStream(out);
      return Optional.of(out.toByteArray());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
