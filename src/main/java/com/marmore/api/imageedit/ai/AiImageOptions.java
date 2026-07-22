package com.marmore.api.imageedit.ai;

import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * Opcoes da chamada de edicao de imagem para o provedor OpenAI. Anloga a {@code
 * org.springframework.ai.image.ImageOptions}, porem como record imutavel com static factory (Item
 * 1, Effective Java). Carrega os campos que o endpoint {@code /v1/images/edits} aceita. Substitui o
 * antigo {@code EditOptions} (domain), agora removido por ficar sem caller apos a refatoracao para
 * o gateway.
 *
 * @param model modelo (ex.: {@code gpt-image-2})
 * @param n numero de imagens
 * @param size tamanho literal do endpoint ({@code 1024x1024}, etc.)
 * @param quality qualidade ({@code low}/{@code medium}/{@code high}/{@code auto})
 * @param inputFidelity fidelidade do input (modelos 1.x)
 */
public record AiImageOptions(
    @Nullable String model,
    @Nullable Integer n,
    @Nullable String size,
    @Nullable String quality,
    @Nullable String inputFidelity) {

  /** Modelos que suportam {@code input_fidelity} no endpoint de edicao. */
  private static final Set<String> FIDELITY_MODELS =
      Set.of("gpt-image-1", "gpt-image-1.5", "gpt-image-1-mini");

  /** Factory estatica com defaults do produto (Item 1, Effective Java). */
  public static AiImageOptions defaults() {
    return new AiImageOptions("gpt-image-2", 1, "1024x1024", "medium", null);
  }

  /** Indica se {@code input_fidelity} deve ser enviado no multipart (modelos 1.x). */
  public boolean sendsFidelity() {
    return inputFidelity != null && model != null && FIDELITY_MODELS.contains(model);
  }
}
