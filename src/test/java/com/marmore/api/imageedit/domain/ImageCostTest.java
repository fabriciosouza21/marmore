package com.marmore.api.imageedit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes de {@link ImageCost}: carrega os valores em moeda (USD, BRL) sem logica. */
class ImageCostTest {

  @DisplayName("mantem os valores passados (USD e BRL)")
  @Test
  void mantemValoresPassados() {
    BigDecimal usd = new BigDecimal("0.04");
    BigDecimal brl = new BigDecimal("0.22");

    ImageCost cost = new ImageCost(usd, brl);

    assertThat(cost.costUsd()).isEqualByComparingTo(usd);
    assertThat(cost.costBrl()).isEqualByComparingTo(brl);
  }
}
