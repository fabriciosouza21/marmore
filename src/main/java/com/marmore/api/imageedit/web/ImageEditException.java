package com.marmore.api.imageedit.web;

import org.springframework.http.HttpStatus;

/**
 * Excecao de dominio da camada reativa (WebFlux). Carrega o status HTTP e a mensagem para o {@link
 * org.springframework.web.server.WebExceptionHandler} traduzir. Estende {@link RuntimeException}
 * para ser lancada sem ser declarada.
 *
 * <p>Reservada: o fluxo de erro do SSE trafega via {@code GenerateResult.Err}; esta excecao nao e
 * lancada em producao hoje, mas fica para um futuro caller nao-SSE/HTTP-error ja tratado pelo
 * GlobalWebExceptionHandler.
 */
public final class ImageEditException extends RuntimeException {

  private final HttpStatus status;

  /**
   * Construtor.
   *
   * @param status status HTTP a ser devolvido
   * @param message mensagem de erro (vinda do {@code GenerateResult.Err})
   */
  public ImageEditException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  /**
   * Devolve o status HTTP carregado por esta excecao.
   *
   * @return o status HTTP carregado
   */
  public HttpStatus getStatus() {
    return status;
  }
}
