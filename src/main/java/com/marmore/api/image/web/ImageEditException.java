package com.marmore.api.image.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Excecao de dominio para erros do endpoint de edicao. Carrega status HTTP apropriado a cada caso.
 */
public class ImageEditException extends ResponseStatusException {

  /**
   * Construtor.
   *
   * @param status status HTTP
   * @param message mensagem de erro (vinda do GenerateResult.Err)
   */
  public ImageEditException(HttpStatus status, String message) {
    super(status, message);
  }
}
