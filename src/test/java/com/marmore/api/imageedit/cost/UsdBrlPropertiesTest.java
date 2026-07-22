package com.marmore.api.imageedit.cost;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes de {@link UsdBrlProperties}: o construtor canonico com args {@code null} deve entregar os
 * tres defaults (URL da API de cambio, TTL do cache e fallback do dolar) definidos no {@code
 * task-3-brief}.
 */
class UsdBrlPropertiesTest {

  @DisplayName("construtor canonico com nulls entrega a URL default da AwesomeAPI")
  @Test
  void construtorCanonicoComNullsEntregaUrlDefault() {
    UsdBrlProperties props = new UsdBrlProperties(null, null, null);

    assertThat(props.url()).isEqualTo("https://economia.awesomeapi.com.br/json/last/USD-BRL");
  }

  @DisplayName("construtor canonico com nulls entrega cacheTtl de 6 horas")
  @Test
  void construtorCanonicoComNullsEntregaCacheTtlDefault() {
    UsdBrlProperties props = new UsdBrlProperties(null, null, null);

    assertThat(props.cacheTtl()).isEqualTo(Duration.ofHours(6));
  }

  @DisplayName("construtor canonico com nulls entrega fallback 5.1075 (Investing 17/07/2026)")
  @Test
  void construtorCanonicoComNullsEntregaFallbackDefault() {
    UsdBrlProperties props = new UsdBrlProperties(null, null, null);

    assertThat(props.fallback()).isEqualByComparingTo(new BigDecimal("5.1075"));
  }
}
