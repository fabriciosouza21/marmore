package com.marmore.api.imageedit;

import java.io.IOException;
import org.springframework.core.io.ClassPathResource;

/** Acesso a imagem de ambiente de teste no classpath. */
public final class TestImages {

  private static final String DIR = "test-images/";

  private TestImages() {}

  /** Bytes da imagem de ambiente. */
  public static byte[] ambiente() throws IOException {
    return bytesOf();
  }

  private static byte[] bytesOf() throws IOException {
    return new ClassPathResource(DIR + "ambiente.png").getContentAsByteArray();
  }
}
