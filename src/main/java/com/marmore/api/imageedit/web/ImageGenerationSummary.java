package com.marmore.api.imageedit.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Resumo dos metadados de uma imagem gerada no contrato do {@code GET /images}: JSON snake_case em
 * PT ({@code criado_em}, {@code custo_brl}, {@code latencia_ms}), com {@code criado_em} como
 * ISO-8601 e {@code custo_brl} nulo quando nao foi possivel calcular. {@code nome_pedra} e o nome
 * comercial resolvido do catalogo de pedras e fica nulo quando o id da pedra e nulo ou nao
 * encontrado. {@code nome_produto} e o nome de exibicao resolvido do catalogo de produtos e fica
 * nulo quando o produto nao esta gravado ou nao e reconhecido.
 */
record ImageGenerationSummary(
    UUID id,
    @JsonProperty("criado_em") Instant criadoEm,
    String modelo,
    @JsonProperty("custo_brl") BigDecimal custoBrl,
    @JsonProperty("latencia_ms") long latenciaMs,
    String pedra,
    @JsonProperty("nome_pedra") String nomePedra,
    String produto,
    @JsonProperty("nome_produto") String nomeProduto) {}
