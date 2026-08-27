package com.marmore.api.imageedit.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Testes de {@link StorageProperties} (validacao na inicializacao). */
class StoragePropertiesTest {

  /** Falha (lanca) quando qualquer propriedade obrigatoria esta ausente ou em branco. */
  @Test
  void falhaQuandoPropriedadeEmBranco() {
    StorageProperties props = new StorageProperties();
    props.setEndpoint("http://localhost:9000");
    props.setAccessKey("marmore");
    props.setSecretKey(" ");
    props.setBucket("marmore-imagens-geradas");

    assertThatThrownBy(props::validar)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("marmore.storage");
  }

  /** Falha quando nenhuma propriedade e definida. */
  @Test
  void falhaQuandoNenhumaPropriedadeDefinida() {
    StorageProperties props = new StorageProperties();

    assertThatThrownBy(props::validar).isInstanceOf(IllegalStateException.class);
  }

  /** Aceita quando todas as propriedades estao definidas. */
  @Test
  void aceitaQuandoPropriedadesDefinidas() {
    StorageProperties props = new StorageProperties();
    props.setEndpoint("http://localhost:9000");
    props.setAccessKey("marmore");
    props.setSecretKey("marmore123");
    props.setBucket("marmore-imagens-geradas");

    props.validar(); // nao lanca
    assertThat(props.getBucket()).isEqualTo("marmore-imagens-geradas");
  }
}
