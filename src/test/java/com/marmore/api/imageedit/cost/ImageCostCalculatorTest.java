package com.marmore.api.imageedit.cost;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes de {@link ImageCostCalculator}: lookup na tabela hardcodeada (model x quality x size), com
 * resolucao de {@code auto} e retorno de {@link Optional#empty()} para combinacoes ausentes.
 */
class ImageCostCalculatorTest {

  private final ImageCostCalculator calculator = new ImageCostCalculator();

  @DisplayName("defaults do produto retornam 0.006 USD (gpt-image-2 + low + 1024x1024)")
  @Test
  void defaultsDoProdutoRetornaZeroSeis() {
    Optional<BigDecimal> cost = calculator.costUsd("gpt-image-2", "low", "1024x1024");

    assertThat(cost).hasValue(new BigDecimal("0.006"));
  }

  @DisplayName("auto em quality e size resolve para medium e 1024x1024")
  @Test
  void autoEmQualitySizeResolvidoParaDefaults() {
    Optional<BigDecimal> cost = calculator.costUsd("gpt-image-2", "auto", "auto");

    assertThat(cost).hasValue(new BigDecimal("0.053"));
  }

  @DisplayName("apenas auto em quality resolve para medium")
  @Test
  void autoEmQualityResolvidoParaMedium() {
    Optional<BigDecimal> cost = calculator.costUsd("gpt-image-2", "auto", "1024x1024");

    assertThat(cost).hasValue(new BigDecimal("0.053"));
  }

  @DisplayName("apenas auto em size resolve para 1024x1024")
  @Test
  void autoEmSizeResolvidoPara1024() {
    Optional<BigDecimal> cost = calculator.costUsd("gpt-image-2", "low", "auto");

    assertThat(cost).hasValue(new BigDecimal("0.006"));
  }

  @DisplayName("combinação conhecida retorna o valor exato da tabela")
  @Test
  void combinacaoConhecidaRetornaValorDaTabela() {
    Optional<BigDecimal> cost = calculator.costUsd("gpt-image-1.5", "high", "1024x1536");

    assertThat(cost).hasValue(new BigDecimal("0.200"));
  }

  @DisplayName("combinação conhecida do gpt-image-1-mini retorna o valor da tabela")
  @Test
  void combinacaoMiniRetornaValorDaTabela() {
    Optional<BigDecimal> cost = calculator.costUsd("gpt-image-1-mini", "medium", "1536x1024");

    assertThat(cost).hasValue(new BigDecimal("0.015"));
  }

  @DisplayName("modelo desconhecido retorna Optional vazio")
  @Test
  void modeloDesconhecidoRetornaVazio() {
    Optional<BigDecimal> cost = calculator.costUsd("dall-e-3", "low", "1024x1024");

    assertThat(cost).isEmpty();
  }

  @DisplayName("quality desconhecida retorna Optional vazio")
  @Test
  void qualityDesconhecidaRetornaVazio() {
    Optional<BigDecimal> cost = calculator.costUsd("gpt-image-2", "ultra", "1024x1024");

    assertThat(cost).isEmpty();
  }

  @DisplayName("size desconhecido retorna Optional vazio")
  @Test
  void sizeDesconhecidoRetornaVazio() {
    Optional<BigDecimal> cost = calculator.costUsd("gpt-image-2", "low", "768x768");

    assertThat(cost).isEmpty();
  }
}
