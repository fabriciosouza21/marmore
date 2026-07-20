package com.marmore.api.image.domain;

import tools.jackson.databind.JsonNode;

/**
 * Resultado de uma chamada de geracao de imagem. Type-safe: sucesso ou erro sao representados como
 * instancias distintas, sem lancar excecao.
 */
public sealed interface GenerateResult permits GenerateResult.Ok, GenerateResult.Err {

  /** Resultado de sucesso: imagem em base64, JSON cru, uso de tokens e latencia. */
  record Ok(String b64, JsonNode raw, JsonNode usage, long latencyMs) implements GenerateResult {}

  /** Resultado de erro: mensagem amigavel e latencia medida ate a falha. */
  record Err(String error, long latencyMs) implements GenerateResult {}
}
