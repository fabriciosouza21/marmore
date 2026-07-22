package com.marmore.api.imageedit.ai;

import reactor.core.publisher.Mono;

/**
 * Contrato reativo de um modelo de edicao de imagem. Versao reativa do antigo {@code
 * com.marmore.api.image.ai.ImageEditModel}: o metodo {@link #call(ImageEditPrompt)} retorna {@link
 * Mono}&lt;{@link ImageResponse}&gt;, que completa quando o provedor (OpenAI) responde. Necessario
 * porque o endpoint SSE e reativo (WebFlux).
 *
 * <p>Interface funcional com um unico metodo {@code call}, que recebe um {@link ImageEditPrompt}
 * (instrucoes de texto e imagens binarias de entrada) e devolve o {@link Mono} reativo.
 */
@FunctionalInterface
public interface ImageEditModel {

  /**
   * Executa a chamada ao modelo de edicao de imagem de forma reativa.
   *
   * @param prompt prompt de edicao (instrucoes + opcoes + imagens de entrada)
   * @return {@link Mono} que emitira a {@link ImageResponse} quando o provedor responder
   */
  Mono<ImageResponse> call(ImageEditPrompt prompt);
}
