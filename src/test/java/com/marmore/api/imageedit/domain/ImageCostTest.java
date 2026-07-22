package com.marmore.api.imageedit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Testes de {@link ImageCost}: carrega os tres valores (USD, BRL, usage) sem logica. */
class ImageCostTest {

  /** Mantem os valores passados (USD, BRL e usage nao nulo). */
  @Test
  void mantemValoresPassados() {
    BigDecimal usd = new BigDecimal("0.04");
    BigDecimal brl = new BigDecimal("0.22");
    JsonNode usage = JsonMapper.builder().build().createObjectNode().put("prompt_tokens", 10);

    ImageCost cost = new ImageCost(usd, brl, usage);

    assertThat(cost.costUsd()).isEqualByComparingTo(usd);
    assertThat(cost.costBrl()).isEqualByComparingTo(brl);
    assertThat(cost.usage()).isSameAs(usage);
  }

  /** Aceita {@code usage} null sem estourar (provedor pode nao retornar usage). */
  @Test
  void aceitaUsageNulo() {
    ImageCost cost = new ImageCost(new BigDecimal("0.04"), new BigDecimal("0.22"), null);

    assertThat(cost.usage()).isNull();
  }
}
