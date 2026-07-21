package com.marmore.api.image.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
