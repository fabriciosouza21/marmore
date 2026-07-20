package com.marmore.api.image.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Testes de {@link EditOptions}. */
class EditOptionsTest {

  @Test
  void defaultsPreencheValoresObrigatorios() {
    EditOptions opts = EditOptions.defaults();

    assertThat(opts.model()).isEqualTo("gpt-image-2");
    assertThat(opts.size()).isEqualTo("1024x1024");
    assertThat(opts.quality()).isEqualTo("medium");
    assertThat(opts.inputFidelity()).isNull();
    assertThat(opts.timeout()).isEqualTo(Duration.ofSeconds(180));
  }

  @Test
  void enviaFidelidadeQuandoModeloSuportaComValorInformado() {
    EditOptions opts =
        new EditOptions("gpt-image-1.5", "1024x1024", "medium", "high", Duration.ofSeconds(180));

    assertThat(opts.sendsFidelity()).isTrue();
  }

  @Test
  void omiteFidelidadeQuandoModeloNaoSuportaMesmoComValorInformado() {
    EditOptions opts =
        new EditOptions("gpt-image-2", "1024x1024", "medium", "high", Duration.ofSeconds(180));

    assertThat(opts.sendsFidelity()).isFalse();
  }

  @Test
  void omiteFidelidadeQuandoValorNuloMesmoEmModeloSuportado() {
    EditOptions opts =
        new EditOptions("gpt-image-1", "1024x1024", "medium", null, Duration.ofSeconds(180));

    assertThat(opts.sendsFidelity()).isFalse();
  }
}
