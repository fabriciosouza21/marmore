package com.marmore.api.imageedit.ai;

import java.util.List;
import java.util.Optional;

/**
 * Resposta de uma chamada de geracao de imagem. Anloga a {@code
 * org.springframework.ai.image.ImageResponse}: lista de geracoes + metadados. Imutavel (record). O
 * nome dos getters segue o contrato do Spring AI ({@code getResults}, {@code getResult}, {@code
 * getMetadata}).
 *
 * @param results geracoes retornadas pelo provedor (nao vazia em sucesso)
 * @param metadata metadados da resposta
 */
public record ImageResponse(List<ImageGeneration> results, ImageResponseMetadata metadata) {

  /** Construtor canonical copia defensivamente a lista. */
  public ImageResponse {
    results = List.copyOf(results);
  }

  /** Retorna a primeira geracao, ou empty se vazia. */
  public Optional<ImageGeneration> getResult() {
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  /** Retorna o base64 da primeira geracao, ou empty se nao houver geracao com imagem. */
  public Optional<String> firstB64() {
    return getResult().map(g -> g.output().b64Json());
  }
}
