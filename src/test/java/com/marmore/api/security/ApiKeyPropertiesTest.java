package com.marmore.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Testes de {@link ApiKeyProperties} (validacao na inicializacao). */
class ApiKeyPropertiesTest {

  /** Falha (lanca) quando a chave e ausente ou em branco. */
  @Test
  void falhaQuandoChaveAusente() {
    ApiKeyProperties props = new ApiKeyProperties();
    props.setKey("");

    org.assertj.core.api.Assertions.assertThatThrownBy(props::validar)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("marmore.api.key ausente");
  }

  /** Falha quando a chave e nula. */
  @Test
  void falhaQuandoChaveNula() {
    ApiKeyProperties props = new ApiKeyProperties();

    org.assertj.core.api.Assertions.assertThatThrownBy(props::validar)
        .isInstanceOf(IllegalStateException.class);
  }

  /** Aceita quando a chave esta definida. */
  @Test
  void aceitaQuandoChaveDefinida() {
    ApiKeyProperties props = new ApiKeyProperties();
    props.setKey("chave-valida-123");

    props.validar(); // nao lanca
    assertThat(props.getKey()).isEqualTo("chave-valida-123");
  }
}
