package com.marmore.api.image.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Testes de {@link ImageResponse} e tipos correlatos da borda espelhada do Spring AI. */
class ImageResponseTest {

  /** Factory {@code of} cria resposta com metadata vazia e copia defensiva da lista. */
  @Test
  void ofCriaRespostaComMetadataVaziaCopiaLista() {
    ImageGeneration gen = ImageGeneration.of(Image.ofB64("aGVsbG8="));
    List<ImageGeneration> input = new java.util.ArrayList<>();
    input.add(gen);

    ImageResponse resp = ImageResponse.of(input);

    assertThat(resp.metadata()).isEqualTo(ImageResponseMetadata.empty());
    assertThat(resp.results()).containsExactly(gen);
    input.clear();
    assertThat(resp.results()).hasSize(1);
  }

  /** Construtor canonical copia defensivamente a lista. */
  @Test
  void construtorCanonicalCopiaDefensivamente() {
    ImageGeneration gen = ImageGeneration.of(Image.ofB64("aA=="));
    List<ImageGeneration> input = new java.util.ArrayList<>();
    input.add(gen);

    ImageResponse resp = new ImageResponse(input, ImageResponseMetadata.empty());

    input.clear();
    assertThat(resp.results()).hasSize(1);
  }

  /** {@code getResult} retorna a primeira geracao ou nulo se vazia. */
  @Test
  void getResultRetornaPrimeiraOuNulo() {
    ImageGeneration gen = ImageGeneration.of(Image.ofB64("aA=="));
    ImageResponse comGen = ImageResponse.of(List.of(gen));
    ImageResponse vazia = ImageResponse.of(List.of());

    assertThat(comGen.getResult()).isEqualTo(gen);
    assertThat(vazia.getResult()).isNull();
  }

  /** {@code results} e imutavel. */
  @Test
  void resultsEhImutavel() {
    ImageResponse resp = ImageResponse.of(List.of(ImageGeneration.of(Image.ofB64("aA=="))));

    assertThatThrownBy(() -> resp.results().add(ImageGeneration.of(Image.ofB64("bA=="))))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  /** Factories de {@link Image} distinguem url e b64. */
  @Test
  void imageFactoriesDistinguemUrlEb64() {
    Image porUrl = Image.ofUrl("https://exemplo/img.png");
    Image porB64 = Image.ofB64("aA==");

    assertThat(porUrl.url()).isEqualTo("https://exemplo/img.png");
    assertThat(porUrl.b64Json()).isNull();
    assertThat(porB64.b64Json()).isEqualTo("aA==");
    assertThat(porB64.url()).isNull();
  }

  /** {@link ImageMessage#of} cria mensagem sem peso. */
  @Test
  void imageMessageOfCriaSemPeso() {
    ImageMessage msg = ImageMessage.of("prompt");

    assertThat(msg.text()).isEqualTo("prompt");
    assertThat(msg.weight()).isNull();
  }
}
