package com.marmore.api.imageedit.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Testes do contrato reativo de {@link ImageEditModel}. A interface e funcional: qualquer lambda
 * que receba {@link ImageEditPrompt} e devolva um {@link Mono} satisfaz o contrato. Estes testes
 * verificam que o tipo existe, e funcional, e que uma implementacao lambda retorna um {@link Mono}.
 */
class ImageEditModelTest {

  @Test
  @DisplayName("Lambda que retorna Mono<ImageResponse> satisfaz o contrato funcional")
  void lambdaRetornaMonoSatisfazContrato() {
    ImageEditModel model = prompt -> Mono.just(respostaFixa());

    Mono<ImageResponse> resultado = model.call(promptFixo());

    assertThat(resultado).isNotNull();
    ImageResponse resp = resultado.block();
    assertThat(resp).isEqualTo(respostaFixa());
  }

  @Test
  @DisplayName("Lambda pode retornar Mono vazio (erro assincrono futuro)")
  void lambdaPodeRetornarMonoVazio() {
    ImageEditModel model = prompt -> Mono.empty();

    Mono<ImageResponse> resultado = model.call(promptFixo());

    assertThat(resultado.block()).isNull();
  }

  private static ImageEditPrompt promptFixo() {
    return ImageEditPrompt.of(
        "prompt",
        AiImageOptions.defaults(),
        List.of(InputImage.of(new byte[] {1}, "ambiente.jpg")));
  }

  private static ImageResponse respostaFixa() {
    return new ImageResponse(
        List.of(ImageGeneration.of(Image.of("aA=="))), ImageResponseMetadata.empty(), null);
  }
}
