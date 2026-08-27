package com.marmore.api.imageedit.ai;

import java.util.Arrays;

/**
 * Imagem binaria de entrada para edicao. Conceito ausente no Spring AI, cuja {@code ImageMessage}
 * carrega apenas texto. Aqui modelamos os bytes (ex.: foto do ambiente, swatch da pedra) que o
 * endpoint {@code /v1/images/edits} recebe como multipart. Imutavel (record); o construtor copia o
 * array defensivamente na entrada. O acessor {@link #bytes()} devolve o array interno diretamente:
 * o consumidor (gateway) e dono unico e o repassa a um {@code ByteArrayResource}, que por sua vez
 * copia. Duplicar o clone no getter custaria uma copia integral a cada leitura (ate ~25MB), sem
 * beneficio real.
 *
 * @param bytes conteudo binario da imagem (copia defensiva na construcao)
 * @param filename nome do arquivo usado no multipart
 */
public record InputImage(byte[] bytes, String filename) {

  /** Construtor canonical copia defensivamente o array de bytes na entrada. */
  public InputImage {
    bytes = bytes.clone();
  }

  /** Factory estatica. */
  public static InputImage of(byte[] bytes, String filename) {
    return new InputImage(bytes, filename);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof InputImage(byte[] bytes1, String filename1))) {
      return false;
    }
    return Arrays.equals(bytes, bytes1) && java.util.Objects.equals(filename, filename1);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(filename) + 31 * Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return "InputImage{bytes=<%d bytes>, filename='%s'}".formatted(bytes.length, filename);
  }
}
