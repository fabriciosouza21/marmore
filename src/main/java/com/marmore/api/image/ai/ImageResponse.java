package com.marmore.api.image.ai;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Resposta de uma chamada de geracao de imagem. Espelha {@code
 * org.springframework.ai.image.ImageResponse}: lista de geracoes + metadados. Imutavel (record). O
 * nome dos getters segue o contrato do Spring AI ({@code getResults}, {@code getResult}, {@code
 * getMetadata}) para que a migracao futura seja mecanica.
 *
 * @param results geracoes retornadas pelo provedor (nao vazia em sucesso)
 * @param metadata metadados da resposta
 */
public record ImageResponse(List<ImageGeneration> results, ImageResponseMetadata metadata) {

  /** Factory estatica para resposta com metadata vazia (Item 1, Effective Java). */
  public static ImageResponse of(List<ImageGeneration> results) {
    return new ImageResponse(List.copyOf(results), ImageResponseMetadata.empty());
  }

  /** Construtor canonical copia defensivamente a lista (imutabilidade, Item 17). */
  public ImageResponse {
    results = List.copyOf(results);
  }

  /** Retorna a primeira geracao, ou nulo se vazia. Espelha {@code ImageResponse.getResult()}. */
  @Nullable
  public ImageGeneration getResult() {
    return results.isEmpty() ? null : results.get(0);
  }
}
