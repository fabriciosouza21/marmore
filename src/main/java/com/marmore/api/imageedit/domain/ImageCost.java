package com.marmore.api.imageedit.domain;

import java.math.BigDecimal;

/**
 * Custo de uma geracao de imagem. Valor imutavel que trafega da camada de custo para o evento SSE.
 * Carrega apenas os valores em moeda; {@code BigDecimal} para precisao monetaria.
 *
 * @param costUsd custo em USD
 * @param costBrl custo em BRL
 */
public record ImageCost(BigDecimal costUsd, BigDecimal costBrl) {}
