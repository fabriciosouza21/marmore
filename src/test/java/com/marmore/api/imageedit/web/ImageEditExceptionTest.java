package com.marmore.api.imageedit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Testes de {@link ImageEditException}: carrega status HTTP + mensagem e e uma {@link
 * RuntimeException} (pode ser lancada sem declarar).
 */
class ImageEditExceptionTest {

  @DisplayName("carrega o status HTTP e a mensagem passados")
  @Test
  void carregaStatusMensagem() {
    ImageEditException ex = new ImageEditException(HttpStatus.UNPROCESSABLE_ENTITY, "prompt vazio");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(ex.getMessage()).isEqualTo("prompt vazio");
  }

  @DisplayName("é uma RuntimeException (não checada)")
  @Test
  void ehRuntimeException() {
    ImageEditException ex = new ImageEditException(HttpStatus.BAD_REQUEST, "boom");

    assertThat(ex).isInstanceOf(RuntimeException.class);
  }

  @DisplayName("pode ser lançada sem ser declarada na assinatura")
  @Test
  void podeSerLancadaSemDeclarar() {
    assertThatThrownBy(this::metodoQueLancaSemDeclarar)
        .isInstanceOf(ImageEditException.class)
        .hasMessage("falha reativa");
  }

  /** Metodo sem {@code throws} na assinatura: prova que a excecao e nao checada. */
  private void metodoQueLancaSemDeclarar() {
    throw new ImageEditException(HttpStatus.INTERNAL_SERVER_ERROR, "falha reativa");
  }
}
