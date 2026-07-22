package com.marmore.api.image.ai;

/**
 * Contrato de um modelo de edicao de imagem. Espelha {@code
 * org.springframework.ai.image.ImageModel}: interface funcional com um unico metodo {@code call}. A
 * implementacao atual ({@link OpenAiRestClientImageEditModel}) fala com a OpenAI via {@code
 * RestClient}; quando o Spring AI cobrir multiplas imagens de entrada, basta uma nova impl (ou
 * adapter) plugar aqui.
 *
 * <p>Diferenca em relacao ao Spring AI: o argumento e {@link ImageEditPrompt} (com imagens binarias
 * de entrada), nao {@code ImagePrompt} (texto only). Lancamento de {@link AiImageException} em
 * falha segue a natureza unchecked do {@code call} do Spring AI.
 */
@FunctionalInterface
public interface ImageEditModel {

  /**
   * Executa a chamada ao modelo.
   *
   * @param prompt prompt de edicao (instrucoes + opcoes + imagens de entrada)
   * @return resposta com a(s) geracao(oes) e metadados
   * @throws AiImageException se a chamada falhar (erro HTTP, resposta malformada, etc.)
   */
  ImageResponse call(ImageEditPrompt prompt);
}
