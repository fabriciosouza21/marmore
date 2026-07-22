package com.marmore.api.imageedit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Testes de {@link GenerateResult}. */
class GenerateResultTest {

  @Test
  void okCarregaBase64LatenciaRaw() {
    GenerateResult.Ok ok = new GenerateResult.Ok("b64abc", null, null, 42L);

    assertThat(ok.b64()).isEqualTo("b64abc");
    assertThat(ok.latencyMs()).isEqualTo(42L);
    assertThat(ok.raw()).isNull();
    assertThat(ok.usage()).isNull();
  }

  @Test
  void errCarregaMensagemLatencia() {
    GenerateResult.Err err = new GenerateResult.Err("falhou", 10L);

    assertThat(err.error()).isEqualTo("falhou");
    assertThat(err.latencyMs()).isEqualTo(10L);
  }

  @Test
  void instanciasOkErrImplementamGerarResultado() {
    GenerateResult ok = new GenerateResult.Ok("x", null, null, 1L);
    GenerateResult err = new GenerateResult.Err("y", 2L);

    assertThat(ok).isInstanceOf(GenerateResult.class);
    assertThat(err).isInstanceOf(GenerateResult.class);
  }
}
