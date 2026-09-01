package com.marmore.api.imageedit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testes de {@link CatalogoPedras} (carga e validacao fail-fast do catalogo de pedras). */
class CatalogoPedrasTest {

  @TempDir Path dir;

  @Test
  @DisplayName("Carrega o catalogo e expoe as pedras na ordem do arquivo")
  void carregaCatalogoNaOrdemDoArquivo() throws IOException {
    criarImagem("verde_ubatuba.png");
    criarImagem("preto_sao_gabriel.png");
    escreverCatalogo(
        """
        {"pedras": [
          {"id": "verde_ubatuba", "nome": "Verde Ubatuba", "categoria": "Granitos",
            "arquivo": "verde_ubatuba.png"},
          {"id": "preto_sao_gabriel", "nome": "Preto Sao Gabriel", "categoria": "Granitos",
            "arquivo": "preto_sao_gabriel.png"}
        ]}
        """);

    List<Pedra> pedras = new CatalogoPedras(dir).listar();

    assertThat(pedras)
        .containsExactly(
            new Pedra("verde_ubatuba", "Verde Ubatuba", "Granitos", "verde_ubatuba.png"),
            new Pedra(
                "preto_sao_gabriel", "Preto Sao Gabriel", "Granitos", "preto_sao_gabriel.png"));
  }

  @Test
  @DisplayName("Falha quando o catalogo.json nao existe no diretorio")
  void falhaQuandoCatalogoJsonAusente() {
    assertThatThrownBy(() -> new CatalogoPedras(dir)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Falha quando o catalogo.json esta malformado")
  void falhaQuandoJsonMalformado() throws IOException {
    escreverCatalogo("{pedras: quebrado");

    assertThatThrownBy(() -> new CatalogoPedras(dir)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Falha quando existe id duplicado no catalogo")
  void falhaQuandoIdDuplicado() throws IOException {
    criarImagem("verde_ubatuba.png");
    escreverCatalogo(
        """
        {"pedras": [
          {"id": "verde_ubatuba", "nome": "Verde Ubatuba", "categoria": "Granitos",
            "arquivo": "verde_ubatuba.png"},
          {"id": "verde_ubatuba", "nome": "Verde Ubatuba 2", "categoria": "Granitos",
            "arquivo": "verde_ubatuba.png"}
        ]}
        """);

    assertThatThrownBy(() -> new CatalogoPedras(dir)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Falha quando a imagem referenciada por uma pedra nao existe no diretorio")
  void falhaQuandoImagemReferenciadaNaoExiste() throws IOException {
    escreverCatalogo(
        """
        {"pedras": [
          {"id": "verde_ubatuba", "nome": "Verde Ubatuba", "categoria": "Granitos",
            "arquivo": "nao_existe.png"}
        ]}
        """);

    assertThatThrownBy(() -> new CatalogoPedras(dir)).isInstanceOf(IllegalStateException.class);
  }

  private void escreverCatalogo(String json) throws IOException {
    Files.writeString(dir.resolve("catalogo.json"), json);
  }

  private void criarImagem(String nome) throws IOException {
    Files.write(dir.resolve(nome), new byte[] {1});
  }
}
