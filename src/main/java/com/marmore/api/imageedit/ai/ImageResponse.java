package com.marmore.api.imageedit.ai;

import java.util.List;
import org.springframework.lang.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Resposta de uma chamada de geracao de imagem. Anloga a {@code
 * org.springframework.ai.image.ImageResponse}: lista de geracoes + metadados. Imutavel (record). O
 * nome dos getters segue o contrato do Spring AI ({@code getResults}, {@code getResult}, {@code
 * getMetadata}).
 *
 * <p>Carrega tambem o {@code raw} (JSON cru retornado pelo provedor) para que camadas superiores
 * (ex.: {@code FileSystemResultWriter}) possam persistir a resposta integral.
 *
 * @param results geracoes retornadas pelo provedor (nao vazia em sucesso)
 * @param metadata metadados da resposta
 * @param raw JSON cru retornado pelo provedor
 */
public record ImageResponse(
    List<ImageGeneration> results, ImageResponseMetadata metadata, @Nullable JsonNode raw) {

  /** Construtor canonical copia defensivamente a lista (imutabilidade, Item 17). */
  public ImageResponse {
    results = List.copyOf(results);
  }

  /** Retorna a primeira geracao, ou nulo se vazia. */
  @Nullable
  public ImageGeneration getResult() {
    return results.isEmpty() ? null : results.get(0);
  }
}
