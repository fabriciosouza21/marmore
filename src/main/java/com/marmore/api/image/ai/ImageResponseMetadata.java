package com.marmore.api.image.ai;

import org.springframework.lang.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Metadados de uma resposta de geracao de imagem. Anlogo a {@code
 * org.springframework.ai.image.ImageResponseMetadata}. Carrega o consumo reportado pelo provedor
 * (tokens etc.) como JSON cru. Imutavel (record).
 *
 * @param usage consumo reportado pelo provedor, ou nulo se ausente
 */
public record ImageResponseMetadata(@Nullable JsonNode usage) {

  /** Factory estatica para metadata sem uso (Item 1, Effective Java). */
  public static ImageResponseMetadata empty() {
    return new ImageResponseMetadata(null);
  }
}
