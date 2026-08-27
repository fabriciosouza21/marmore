package com.marmore.api.imageedit.domain;

import java.math.BigDecimal;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Resultado de uma chamada de geracao de imagem. Type-safe: sucesso ou erro sao representados como
 * instancias distintas, sem lancar excecao.
 *
 * <p>{@link Ok} carrega tambem o {@link ImageCost custo} computado (USD x cotacao BRL) quando
 * disponivel; pode ser {@code null} se a combinacao modelo/qualidade/tamanho nao existir na tabela
 * de precos ou se a cotacao falhar de forma irrecuperavel.
 */
public sealed interface GenerateResult permits GenerateResult.Ok, GenerateResult.Err {

  /**
   * Resultado de sucesso: imagem em base64, uso de tokens, latencia e custo computado (ou {@code
   * null}).
   */
  record Ok(String b64, @Nullable JsonNode usage, long latencyMs, @Nullable ImageCost cost)
      implements GenerateResult {

    /** Retorna o custo em BRL, ou empty quando o custo nao foi computado. */
    public Optional<BigDecimal> custoBrl() {
      return cost != null ? Optional.of(cost.costBrl()) : Optional.empty();
    }
  }

  /** Resultado de erro: mensagem amigavel e latencia medida ate a falha. */
  record Err(String error, long latencyMs) implements GenerateResult {}
}
