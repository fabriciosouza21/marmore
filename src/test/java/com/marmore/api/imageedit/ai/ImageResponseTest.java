package com.marmore.api.imageedit.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes de {@link ImageResponse} e tipos correlatos da camada ai. */
class ImageResponseTest {

  @Test
  @DisplayName("Construtor canonical copia defensivamente a lista de geracoes")
  void construtorCanonicalCopiaDefensivamente() {
    ImageGeneration gen = ImageGeneration.of(Image.of("aA=="));
    List<ImageGeneration> input = new java.util.ArrayList<>();
    input.add(gen);

    ImageResponse resp = new ImageResponse(input, ImageResponseMetadata.empty(), null);

    input.clear();
    assertThat(resp.results()).hasSize(1);
  }

  @Test
  @DisplayName("getResult retorna a primeira geracao ou empty se vazia")
  void getResultRetornaPrimeiraOuEmpty() {
    ImageGeneration gen = ImageGeneration.of(Image.of("aA=="));
    ImageResponse comGen = new ImageResponse(List.of(gen), ImageResponseMetadata.empty(), null);
    ImageResponse vazia = new ImageResponse(List.of(), ImageResponseMetadata.empty(), null);

    assertThat(comGen.getResult()).hasValue(gen);
    assertThat(vazia.getResult()).isEmpty();
  }

  @Test
  @DisplayName("results e imutavel")
  void resultsEhImutavel() {
    ImageResponse resp =
        new ImageResponse(
            List.of(ImageGeneration.of(Image.of("aA=="))), ImageResponseMetadata.empty(), null);

    assertThatThrownBy(() -> resp.results().add(ImageGeneration.of(Image.of("bA=="))))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("raw e propagado para camadas superiores persistirem o JSON integral")
  void rawPropagadoParaCamadasSuperiores() {
    tools.jackson.databind.JsonNode node =
        tools.jackson.databind.json.JsonMapper.builder().build().createArrayNode();

    ImageResponse resp =
        new ImageResponse(
            List.of(ImageGeneration.of(Image.of("aA=="))), ImageResponseMetadata.empty(), node);

    assertThat(resp.raw()).isSameAs(node);
  }

  @Test
  @DisplayName("Image carrega apenas o base64")
  void imageCarregaB64() {
    Image img = Image.of("aA==");

    assertThat(img.b64Json()).isEqualTo("aA==");
  }
}
