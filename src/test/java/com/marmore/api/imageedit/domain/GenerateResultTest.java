package com.marmore.api.imageedit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes de {@link GenerateResult}. */
class GenerateResultTest {

  @Test
  void okCarregaBase64LatenciaCusto() {
    GenerateResult.Ok ok = new GenerateResult.Ok("b64abc", null, 42L, null);

    assertThat(ok.b64()).isEqualTo("b64abc");
    assertThat(ok.latencyMs()).isEqualTo(42L);
    assertThat(ok.usage()).isNull();
    assertThat(ok.cost()).isNull();
  }

  @Test
  void errCarregaMensagemLatencia() {
    GenerateResult.Err err = new GenerateResult.Err("falhou", 10L);

    assertThat(err.error()).isEqualTo("falhou");
    assertThat(err.latencyMs()).isEqualTo(10L);
  }

  @Test
  void instanciasOkErrImplementamGerarResultado() {
    GenerateResult ok = new GenerateResult.Ok("x", null, 1L, null);
    GenerateResult err = new GenerateResult.Err("y", 2L);

    assertThat(ok).isInstanceOf(GenerateResult.class);
    assertThat(err).isInstanceOf(GenerateResult.class);
  }

  @DisplayName("custoBrl retorna BRL do cost ou empty quando cost e nulo")
  @Test
  void custoBrlRetornaBrlOuEmpty() {
    GenerateResult.Ok semCusto = new GenerateResult.Ok("x", null, 1L, null);
    GenerateResult.Ok comCusto =
        new GenerateResult.Ok(
            "x", null, 1L, new ImageCost(new BigDecimal("0.01"), new BigDecimal("0.05")));

    assertThat(semCusto.custoBrl()).isEmpty();
    assertThat(comCusto.custoBrl()).hasValue(new BigDecimal("0.05"));
  }
}
