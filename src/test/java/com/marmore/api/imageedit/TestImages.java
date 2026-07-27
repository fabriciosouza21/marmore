package com.marmore.api.imageedit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/** Acesso as imagens de teste no classpath (ambiente e granito). */
public final class TestImages {

  private static final String DIR = "test-images/";

  private static final Map<String, String> NAMES =
      Map.of("ambiente", "ambiente.png", "granito", "granito-test.png");

  private TestImages() {}

  /** Bytes da imagem de ambiente. */
  public static byte[] ambiente() throws IOException {
    return bytesOf();
  }

  /** Caminho da imagem de ambiente no classpath. */
  public static Path ambientePath() throws IOException {
    return pathOf("ambiente");
  }

  /** Caminho da imagem de granito no classpath. */
  public static Path granitoPath() throws IOException {
    return pathOf("granito");
  }

  private static byte[] bytesOf() throws IOException {
    var nome = NAMES.get("ambiente");
    if (nome == null) {
      throw new IllegalArgumentException("imagem desconhecida: " + "ambiente");
    }
    return new ClassPathResource(DIR + nome).getContentAsByteArray();
  }

  private static Path pathOf(String imagem) throws IOException {
    var nome = NAMES.get(imagem);
    if (nome == null) {
      throw new IllegalArgumentException("imagem desconhecida: " + imagem);
    }
    return new ClassPathResource(DIR + nome).getFile().toPath();
  }
}
