package com.marmore.api.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Tratamento global de excecoes HTTP. Restaura o contrato de status para erros que, com a cadeia de
 * seguranca ativa, poderiam ser convertidos pelo Spring Security em 403 em vez do status semantico
 * correto.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Upload que excede o limite ({@code spring.servlet.multipart.max-file-size}) devolve 413 Payload
   * Too Large, nao 403. Sem este handler, o {@code ExceptionTranslationFilter} do Spring Security
   * pode capturar a excecao e responder 403, mascarando a semantica de tamanho.
   *
   * @param e excecao de tamanho de upload
   * @return 413 com mensagem
   */
  @org.springframework.web.bind.annotation.ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<String> tamanhoExcedido(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body("{\"error\":\"upload excede o tamanho maximo\"}");
  }
}
