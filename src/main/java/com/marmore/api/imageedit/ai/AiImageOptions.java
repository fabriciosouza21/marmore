package com.marmore.api.imageedit.ai;

import com.marmore.api.imageedit.model.ImageModel;
import com.marmore.api.imageedit.model.ImageQuality;
import com.marmore.api.imageedit.model.ImageSize;
import org.jspecify.annotations.Nullable;

/**
 * Opcoes da chamada de edicao de imagem para o provedor OpenAI. Anloga a {@code
 * org.springframework.ai.image.ImageOptions}, porem como record imutavel com static factory.
 * Carrega os campos que o endpoint {@code /v1/images/edits} aceita. Substitui o antigo {@code
 * EditOptions} (domain), agora removido por ficar sem caller apos a refatoracao para o gateway.
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

  /** Factory estatica com defaults do produto. */
  public static AiImageOptions defaults() {
    return new AiImageOptions(
        ImageModel.GPT_IMAGE_2.apiValue(),
        1,
        ImageSize.SQUARE.apiValue(),
        ImageQuality.LOW.apiValue(),
        null);
  }

  /** Indica se {@code input_fidelity} deve ser enviado no multipart (modelos que suportam). */
  public boolean sendsFidelity() {
    return inputFidelity != null
        && ImageModel.fromApiValue(model).map(ImageModel::supportsFidelity).orElse(false);
  }
}
