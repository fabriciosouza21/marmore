package com.marmore.api.image.ai;

import org.springframework.lang.Nullable;

/**
 * Implementacao imutavel de {@link ImageOptions} para o provedor OpenAI. Espelha o papel de {@code
 * OpenAiImageOptions} do Spring AI, mas em forma de record com static factory (Item 1, Effective
 * Java). Carrega os campos que o endpoint {@code /v1/images/edits} aceita. Os campos de tamanho e
 * qualidade do endpoint OpenAI (nao previstos na interface base do Spring AI) ficam aqui como
 * extras usados pelo gateway.
 *
 * @param model modelo (ex.: {@code gpt-image-2})
 * @param n numero de imagens
 * @param width largura em pixels
 * @param height altura em pixels
 * @param responseFormat formato da resposta
 * @param style estilo
 * @param size tamanho literal do endpoint ({@code 1024x1024}, etc.)
 * @param quality qualidade ({@code low}/{@code medium}/{@code high}/{@code auto})
 * @param inputFidelity fidelidade do input (modelos 1.x)
 */
public record AiImageOptions(
    @Nullable String model,
    @Nullable Integer n,
    @Nullable Integer width,
    @Nullable Integer height,
    @Nullable String responseFormat,
    @Nullable String style,
    @Nullable String size,
    @Nullable String quality,
    @Nullable String inputFidelity)
    implements ImageOptions {

  /** Factory estatica com defaults do produto (Item 1, Effective Java). */
  public static AiImageOptions defaults() {
    return new AiImageOptions(
        "gpt-image-2", 1, null, null, null, null, "1024x1024", "medium", null);
  }

  // Acessadores no estilo JavaBean (getXxx) para aderir ao contrato de ImageOptions, que espelha
  // o Spring AI. O record expoe os mesmos valores via acessadores sem prefixo (model(), n(), ...).

  @Override
  @Nullable
  public String getModel() {
    return model;
  }

  @Override
  @Nullable
  public Integer getN() {
    return n;
  }

  @Override
  @Nullable
  public Integer getWidth() {
    return width;
  }

  @Override
  @Nullable
  public Integer getHeight() {
    return height;
  }

  @Override
  @Nullable
  public String getResponseFormat() {
    return responseFormat;
  }

  @Override
  @Nullable
  public String getStyle() {
    return style;
  }

  /** Indica se {@code input_fidelity} deve ser enviado no multipart (modelos 1.x). */
  public boolean sendsFidelity() {
    if (inputFidelity == null || model == null) {
      return false;
    }
    return model.startsWith("gpt-image-1");
  }
}
