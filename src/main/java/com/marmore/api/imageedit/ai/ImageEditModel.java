package com.marmore.api.imageedit.ai;

import reactor.core.publisher.Mono;

/**
 * Contrato reativo de um modelo de edicao de imagem. Versao reativa do antigo {@code
 * com.marmore.api.image.ai.ImageEditModel}: o metodo {@link #call(ImageEditPrompt)} agora retorna
 * {@link Mono}&lt;{@link ImageResponse}&gt;, que completa quando o provedor (OpenAI) responde.
 * Necessario porque o endpoint SSE e reativo (WebFlux).
 *
 * <p>Espelha o desenho do Spring AI ({@code ImageModel}) como interface funcional com um unico
 * metodo {@code call}. A diferenca em relacao ao Spring AI e o argumento {@link ImageEditPrompt}
 * (que carrega imagens binarias de entrada alem do texto) e o retorno reativo. Quando o Spring AI
 * cobrir multiplas imagens de entrada, basta um adapter plugar aqui.
 *
 * <p>Tratamento de erros: falhas devem ser sinalizadas dentro do {@link Mono} (ex.: via {@code
 * Mono.error} com {@link AiImageException}), preservando a natureza lazy do fluxo reativo.
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
