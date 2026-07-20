package com.marmore.api.image.domain;

import java.time.Duration;
import java.util.Set;

/**
 * Opcoes de uma chamada de edicao de imagem.
 *
 * @param model modelo GPT-Image (ex.: {@code gpt-image-2}, {@code gpt-image-1.5})
 * @param size tamanho da imagem ({@code 1024x1024}, {@code 1536x1024}, etc.)
 * @param quality qualidade ({@code low}, {@code medium}, {@code high}, {@code auto})
 * @param inputFidelity fidelidade do input ({@code low}/{@code high} so para modelos 1.x)
 * @param timeout timeout da chamada HTTP
 */
public record EditOptions(
    String model, String size, String quality, String inputFidelity, Duration timeout) {

  /** Modelos que suportam {@code input_fidelity} no endpoint de edicao. */
  private static final Set<String> FIDELITY_MODELS =
      Set.of("gpt-image-1", "gpt-image-1.5", "gpt-image-1-mini");

  /** Cria opcoes com os defaults obrigatorios. */
  public static EditOptions defaults() {
    return new EditOptions("gpt-image-2", "1024x1024", "medium", null, Duration.ofSeconds(180));
  }

  /** Indica se {@code input_fidelity} deve ser enviado no multipart. */
  public boolean sendsFidelity() {
    return inputFidelity != null && FIDELITY_MODELS.contains(model);
  }
}
