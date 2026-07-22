package com.marmore.api.image.ai;

/**
 * Imagem binaria de entrada para edicao. Conceito ausente no Spring AI, cuja {@code ImageMessage}
 * carrega apenas texto. Aqui modelamos os bytes (ex.: foto do ambiente, swatch da pedra) que o
 * endpoint {@code /v1/images/edits} recebe como multipart. Imutavel (record); o construtor copia o
 * array defensivamente na entrada. O acessor {@link #bytes()} devolve o array interno diretamente:
 * o consumidor (gateway) e dono unico e o repassa a um {@code ByteArrayResource}, que por sua vez
 * copia. Duplicar o clone no getter custaria uma copia integral a cada leitura (ate ~25MB), sem
 * beneficio real (Item 17, Effective Java).
 *
 * @param bytes conteudo binario da imagem (copia defensiva na construcao)
 * @param filename nome do arquivo usado no multipart
 */
public record InputImage(byte[] bytes, String filename) {

  /** Construtor canonical copia defensivamente o array de bytes na entrada. */
  public InputImage {
    bytes = bytes.clone();
  }

  /** Factory estatica (Item 1, Effective Java). */
  public static InputImage of(byte[] bytes, String filename) {
    return new InputImage(bytes, filename);
  }
}
