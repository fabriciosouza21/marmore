package com.marmore.api.imageedit.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Custo de uma geracao de imagem. Valor imutavel que trafega da camada de custo para o evento SSE.
 * Carrega tres valores, sem logica: custo em USD, custo em BRL e o {@code usage} cru retornado pelo
 * provedor (OpenAI). {@code BigDecimal} para precisao monetaria; {@code usage} pode ser {@code
 * null} quando o provedor nao o retorna.
 *
 * @param costUsd custo em USD
 * @param costBrl custo em BRL
 * @param usage JSON cru de usage retornado pelo provedor, ou {@code null}; reservada -- o servico
 *     passa {@code null} hoje (o usage real viaja em {@code GenerateResult.Ok.usage}), mantida para
 *     um futuro detalhamento de custo
 */
public record ImageCost(BigDecimal costUsd, BigDecimal costBrl, @Nullable JsonNode usage) {}
