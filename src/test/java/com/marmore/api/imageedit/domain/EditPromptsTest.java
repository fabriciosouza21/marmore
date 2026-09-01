package com.marmore.api.imageedit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Testes de {@link EditPrompts}. */
class EditPromptsTest {

  /** Placeholder do template do countertop, substituido pelo nome comercial da pedra. */
  private static final String PLACEHOLDER_PEDRA = "{{NOME_PEDRA}}";

  @Test
  @SuppressWarnings("AbbreviationAsWordInName")
  void countertopContemMarcadoresDeImagem1EImagem2() {
    String prompt = EditPrompts.COUNTERTOP;

    assertThat(prompt).contains("IMAGE 1");
    assertThat(prompt).contains("IMAGE 2");
    assertThat(prompt).contains("drainboard");
    assertThat(prompt).startsWith("I am sending two images.");
  }

  @Test
  @DisplayName(
      "Afirma que o template EditPrompts.COUNTERTOP e byte-igual a fonte canonica em"
          + " prompts/prompt-countertop.md")
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

  @Test
  @DisplayName(
      "interpolacao: countertop(nome) substitui o placeholder pelo nome comercial da pedra,"
          + " sem sobras")
  void countertopInterpolaNomeDaPedraSemDeixarPlaceholder() {
    String prompt = EditPrompts.countertop("Verde Ubatuba");

    assertThat(prompt).contains("Verde Ubatuba");
    assertThat(prompt).doesNotContain(PLACEHOLDER_PEDRA);
    assertThat(prompt).doesNotContain("{{");
    assertThat(prompt)
        .isEqualTo(EditPrompts.COUNTERTOP.replace(PLACEHOLDER_PEDRA, "Verde Ubatuba"));
  }

  @Test
  @DisplayName("template neutro: sem travas de cor do granito verde fixo")
  void countertopNaoContemTravasDeCorDoGranito() {
    String prompt = EditPrompts.COUNTERTOP;

    assertThat(prompt).doesNotContain("GREEN");
    assertThat(prompt).doesNotContain("granite material");
  }
}
