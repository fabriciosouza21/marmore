package com.marmore.api.image.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Testes de {@link ImageResponse} e tipos correlatos da camada ai. */
class ImageResponseTest {

  /** Construtor canonical copia defensivamente a lista de geracoes. */
  @Test
  void construtorCanonicalCopiaDefensivamente() {
    ImageGeneration gen = ImageGeneration.of(Image.of("aA=="));
    List<ImageGeneration> input = new java.util.ArrayList<>();
    input.add(gen);

    ImageResponse resp = new ImageResponse(input, ImageResponseMetadata.empty(), null);

    input.clear();
    assertThat(resp.results()).hasSize(1);
  }

  /** {@code getResult} retorna a primeira geracao ou nulo se vazia. */
  @Test
  void getResultRetornaPrimeiraOuNulo() {
    ImageGeneration gen = ImageGeneration.of(Image.of("aA=="));
    ImageResponse comGen = new ImageResponse(List.of(gen), ImageResponseMetadata.empty(), null);
    ImageResponse vazia = new ImageResponse(List.of(), ImageResponseMetadata.empty(), null);

    assertThat(comGen.getResult()).isEqualTo(gen);
    assertThat(vazia.getResult()).isNull();
  }

  /** {@code results} e imutavel. */
  @Test
  void resultsEhImutavel() {
    ImageResponse resp =
        new ImageResponse(
            List.of(ImageGeneration.of(Image.of("aA=="))), ImageResponseMetadata.empty(), null);

    assertThatThrownBy(() -> resp.results().add(ImageGeneration.of(Image.of("bA=="))))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  /** {@code raw} e propagado para camadas superiores persistirem o JSON integral. */
  @Test
  void rawPropagadoParaCamadasSuperiores() {
    tools.jackson.databind.JsonNode node =
        tools.jackson.databind.json.JsonMapper.builder().build().createArrayNode();

    ImageResponse resp =
        new ImageResponse(
            List.of(ImageGeneration.of(Image.of("aA=="))), ImageResponseMetadata.empty(), node);

    assertThat(resp.raw()).isSameAs(node);
  }

  /** {@link Image} carrega apenas o base64. */
  @Test
  void imageCarregaB64() {
    Image img = Image.of("aA==");

    assertThat(img.b64Json()).isEqualTo("aA==");
  }
}
