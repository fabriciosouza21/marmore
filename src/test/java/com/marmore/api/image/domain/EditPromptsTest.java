package com.marmore.api.image.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Testes de {@link EditPrompts}. */
class EditPromptsTest {

  @Test
  @SuppressWarnings("AbbreviationAsWordInName")
  void countertopContemMarcadoresDeImagem1EImagem2() {
    String prompt = EditPrompts.COUNTERTOP;

    assertThat(prompt).contains("IMAGE 1");
    assertThat(prompt).contains("IMAGE 2");
    assertThat(prompt).contains("drainboard");
    assertThat(prompt).startsWith("I am sending two images.");
  }

  /**
   * Afirma que {@link EditPrompts#COUNTERTOP} e byte-igual a fonte canonica em {@code
   * prompts/prompt-countertop.md}. Diferencas de whitespace a direita sao ignoradas (stripTrailing
   * em ambos os lados) para evitar falso negativo por conta do fechamento do text block.
   */
  @Test
  void countertopIgualaFonteCanonica() throws IOException {
    String canonico;
    try (var in = new ClassPathResource("prompts/prompt-countertop.md").getInputStream()) {
      canonico = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    // Text blocks do Java adicionam um \n final (fechamento """); a fonte canonica tambem termina
    // com \n. Comparamos com stripTrailing nos dois lados para pegar drift real, nao pedantismo de
    // whitespace.
    assertThat(EditPrompts.COUNTERTOP.stripTrailing()).isEqualTo(canonico.stripTrailing());
  }
}
