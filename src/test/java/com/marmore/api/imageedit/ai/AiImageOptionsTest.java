package com.marmore.api.imageedit.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes de {@link AiImageOptions}. Migrados do antigo EditOptions (domain), agora removido. */
class AiImageOptionsTest {

  @Test
  @DisplayName("Defaults preenche os valores obrigatorios do produto")
  void defaultsPreencheValoresObrigatorios() {
    AiImageOptions opts = AiImageOptions.defaults();

    assertThat(opts.model()).isEqualTo("gpt-image-2");
    assertThat(opts.n()).isEqualTo(1);
    assertThat(opts.size()).isEqualTo("1024x1024");
    assertThat(opts.quality()).isEqualTo("medium");
    assertThat(opts.inputFidelity()).isNull();
  }

  @Test
  @DisplayName("Envia fidelidade quando o modelo suporta (1.x) e o valor foi informado")
  void enviaFidelidadeQuandoModeloSuportaComValorInformado() {
    AiImageOptions opts = new AiImageOptions("gpt-image-1.5", 1, "1024x1024", "medium", "high");

    assertThat(opts.sendsFidelity()).isTrue();
  }

  @Test
  @DisplayName("Envia fidelidade tambem para o modelo 1-mini (cobertura do Set.of restaurado)")
  void enviaFidelidadeParaModelo1Mini() {
    AiImageOptions opts = new AiImageOptions("gpt-image-1-mini", 1, "1024x1024", "medium", "high");

    assertThat(opts.sendsFidelity()).isTrue();
  }

  @Test
  @DisplayName("Omite fidelidade quando o modelo nao suporta, mesmo com valor informado")
  void omiteFidelidadeQuandoModeloNaoSuportaMesmoComValorInformado() {
    AiImageOptions opts = new AiImageOptions("gpt-image-2", 1, "1024x1024", "medium", "high");

    assertThat(opts.sendsFidelity()).isFalse();
  }

  @Test
  @DisplayName("Omite fidelidade quando o valor e nulo, mesmo em modelo suportado")
  void omiteFidelidadeQuandoValorNuloMesmoEmModeloSuportado() {
    AiImageOptions opts = new AiImageOptions("gpt-image-1", 1, "1024x1024", "medium", null);

    assertThat(opts.sendsFidelity()).isFalse();
  }

  @Test
  @DisplayName("Modelos fora da lista fechada (ex.: gpt-image-10) nao enviam fidelidade")
  void omiteFidelidadeParaModeloForaDaListaFechada() {
    AiImageOptions opts = new AiImageOptions("gpt-image-10", 1, "1024x1024", "medium", "high");

    assertThat(opts.sendsFidelity()).isFalse();
  }
}
