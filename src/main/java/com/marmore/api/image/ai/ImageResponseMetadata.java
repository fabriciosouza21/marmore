package com.marmore.api.image.ai;

import org.springframework.lang.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Metadados de uma resposta de geracao de imagem. Espelha {@code
 * org.springframework.ai.image.ImageResponseMetadata}: carrega o instante de criacao e, via campo
 * livre {@code usage}, o consumo reportado pelo provedor (tokens etc.). Imutavel (record).
 *
 * @param created instante de criacao (epoch millis), ou nulo se ausente
 * @param usage consumo reportado pelo provedor (JSON cru), ou nulo se ausente
 */
public record ImageResponseMetadata(@Nullable Long created, @Nullable JsonNode usage) {

  /** Factory estatica para metadata sem uso (Item 1, Effective Java). */
  public static ImageResponseMetadata empty() {
    return new ImageResponseMetadata(null, null);
  }
}
