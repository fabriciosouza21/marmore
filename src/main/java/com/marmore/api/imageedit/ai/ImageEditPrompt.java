package com.marmore.api.imageedit.ai;

import java.util.List;

/**
 * Prompt de edicao de imagem com multiplas imagens de entrada. Anlogo de {@code
 * org.springframework.ai.image.ImagePrompt}, porem estendido para carregar imagens binarias ({@link
 * InputImage}) alem das instrucoes textuais. Este e o unico ponto de divergencia com o contrato do
 * Spring AI: quando o Spring AI passar a modelar imagens de entrada, basta um adapter traduzir
 * {@code inputImages} para o equivalente la. Imutavel (record); copia defensivamente a lista.
 *
 * <p>A ordem de {@code inputImages} e semantica: o prompt fixo do produto referencia {@code IMAGE
 * 1} (ambiente) e {@code IMAGE 2} (pedra), e a implementacao do gateway preserva essa ordem no
 * multipart enviado a OpenAI.
 *
 * @param instructions texto do prompt
 * @param options opcoes da chamada
 * @param inputImages imagens de entrada, em ordem semantica
 */
public record ImageEditPrompt(
    String instructions, AiImageOptions options, List<InputImage> inputImages) {

  /** Construtor canonical copia defensivamente a lista de imagens (Item 17). */
  public ImageEditPrompt {
    inputImages = List.copyOf(inputImages);
  }

  /** Factory estatica (Item 1, Effective Java). */
  public static ImageEditPrompt of(
      String instructions, AiImageOptions options, List<InputImage> inputImages) {
    return new ImageEditPrompt(instructions, options, inputImages);
  }
}
