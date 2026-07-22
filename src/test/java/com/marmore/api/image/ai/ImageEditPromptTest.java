package com.marmore.api.image.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Testes de {@link InputImage} e {@link ImageEditPrompt} (extensao do gap). */
class ImageEditPromptTest {

  /** {@link InputImage} copia defensivamente os bytes na construcao. */
  @Test
  void inputImageCopiaBytesNaConstrucao() {
    byte[] original = {1, 2, 3};

    InputImage img = InputImage.of(original, "ambiente.jpg");

    original[0] = 99;
    assertThat(img.bytes()).containsExactly(1, 2, 3);
  }

  /** {@link ImageEditPrompt#inputImages()} e imutavel e copia defensivamente a lista de entrada. */
  @Test
  void promptCopiaDefensivamenteListaDeEntrada() {
    InputImage ambiente = InputImage.of(new byte[] {1}, "ambiente.jpg");
    InputImage pedra = InputImage.of(new byte[] {2}, "granito.png");
    List<InputImage> entrada = new java.util.ArrayList<>(List.of(ambiente, pedra));

    ImageEditPrompt prompt = ImageEditPrompt.of("prompt", AiImageOptions.defaults(), entrada);

    entrada.clear();
    assertThat(prompt.inputImages()).containsExactly(ambiente, pedra);
  }

  /** {@link ImageEditPrompt#inputImages()} e imutavel. */
  @Test
  void promptInputImagesEhImutavel() {
    ImageEditPrompt prompt =
        ImageEditPrompt.of(
            "prompt",
            AiImageOptions.defaults(),
            List.of(InputImage.of(new byte[] {1}, "ambiente.jpg")));

    assertThatThrownBy(() -> prompt.inputImages().add(InputImage.of(new byte[] {2}, "x.png")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  /** A ordem das imagens de entrada e preservada (contrato semantico IMAGE 1/IMAGE 2). */
  @Test
  void ordemDasImagensDeEntradaPreservada() {
    InputImage primeira = InputImage.of(new byte[] {1}, "ambiente.jpg");
    InputImage segunda = InputImage.of(new byte[] {2}, "granito.png");

    ImageEditPrompt prompt =
        ImageEditPrompt.of("prompt", AiImageOptions.defaults(), List.of(primeira, segunda));

    assertThat(prompt.inputImages()).containsExactly(primeira, segunda);
  }
}
