package com.marmore.api.imageedit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Testes de {@link ImageResizer}. */
class ImageResizerTest {

  private final ImageResizer resizer = new ImageResizer();

  @Test
  void reduzMaiorLadoPara1536QuandoEntradaMaior() throws Exception {
    byte[] entrada = gerarPngRbg(2000, 1000);

    Optional<byte[]> saidaOpt = resizer.resize(entrada);

    assertThat(saidaOpt).isPresent();
    BufferedImage saida = ImageIO.read(new ByteArrayInputStream(saidaOpt.get()));
    assertThat(Math.max(saida.getWidth(), saida.getHeight())).isLessThanOrEqualTo(1536);
    assertThat(saida.getWidth()).isGreaterThan(saida.getHeight());
  }

  @Test
  void naoFazUpscaleQuandoEntradaMenorQue1536() throws Exception {
    byte[] entrada = gerarPngRbg(800, 600);

    Optional<byte[]> saidaOpt = resizer.resize(entrada);

    assertThat(saidaOpt).isPresent();
    BufferedImage saida = ImageIO.read(new ByteArrayInputStream(saidaOpt.get()));
    assertThat(saida.getWidth()).isEqualTo(800);
    assertThat(saida.getHeight()).isEqualTo(600);
  }

  @Test
  void comprimeSempreMesmoQuandoNaoRedimensiona() throws Exception {
    // PNG sem compressão JPEG: saida JPEG 0.85 deve ser menor que a entrada.
    byte[] entrada = gerarPngRbg(1000, 1000);

    Optional<byte[]> saidaOpt = resizer.resize(entrada);

    assertThat(saidaOpt).isPresent();
    assertThat(saidaOpt.get().length).isLessThan(entrada.length);
  }

  @Test
  void entradaInvalidaDevolveEmptySemLancar() {
    Optional<byte[]> saidaOpt = resizer.resize(new byte[] {1, 2, 3, 4});

    assertThat(saidaOpt).isEmpty();
  }

  /**
   * Gera um PNG RGB com conteúdo ruidoso por pixel e dimensões dadas. O ruído garante que PNG
   * (lossless) não comprima melhor que JPEG q=0.85, permitindo validar a re-codificação mesmo sem
   * redimensionamento.
   */
  private static byte[] gerarPngRbg(int w, int h) throws Exception {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Random rng = new Random(0xC0FFEEL);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        img.setRGB(x, y, rng.nextInt(0x00FFFFFF));
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "png", out);
    return out.toByteArray();
  }
}
