package com.marmore.api.imageedit.cost;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes de {@link UsdBrlProperties}: o construtor sem args deve entregar os tres defaults (URL da
 * API de cambio, TTL do cache e fallback do dolar) definidos no {@code task-3-brief}.
 */
class UsdBrlPropertiesTest {

  @DisplayName("construtor sem args entrega a URL default da AwesomeAPI")
  @Test
  void construtorSemArgsEntregaUrlDefault() {
    UsdBrlProperties props = new UsdBrlProperties();

    assertThat(props.url()).isEqualTo("https://economia.awesomeapi.com.br/json/last/USD-BRL");
  }

  @DisplayName("construtor sem args entrega cacheTtl de 6 horas")
  @Test
  void construtorSemArgsEntregaCacheTtlDefault() {
    UsdBrlProperties props = new UsdBrlProperties();

    assertThat(props.cacheTtl()).isEqualTo(Duration.ofHours(6));
  }

  @DisplayName("construtor sem args entrega fallback 5.1075 (Investing 17/07/2026)")
  @Test
  void construtorSemArgsEntregaFallbackDefault() {
    UsdBrlProperties props = new UsdBrlProperties();

    assertThat(props.fallback()).isEqualByComparingTo(new BigDecimal("5.1075"));
  }
}
