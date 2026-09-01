package com.marmore.api.imageedit.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Catalogo de pedras carregado de {@code catalogo.json} no diretorio informado.
 *
 * <p>Validacao fail-fast no construtor: falha se o arquivo nao existe, se o JSON esta malformado,
 * se ha id duplicado ou se a imagem referenciada por alguma pedra nao existe no diretorio.
 */
public class CatalogoPedras {

  private static final String CATALOGO_JSON = "catalogo.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final List<Pedra> pedras;

  /** Carrega e valida o catalogo do diretorio informado. Falha se estiver invalido. */
  public CatalogoPedras(Path diretorio) {
    pedras = List.copyOf(carregar(diretorio));
  }

  /** Retorna as pedras na ordem definida no catalogo. */
  public List<Pedra> listar() {
    return pedras;
  }

  private static List<Pedra> carregar(Path diretorio) {
    Path arquivoCatalogo = diretorio.resolve(CATALOGO_JSON);
    CatalogoJson catalogo;
    try {
      catalogo = MAPPER.readValue(arquivoCatalogo.toFile(), CatalogoJson.class);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Falha ao carregar " + arquivoCatalogo + ": " + e.getMessage(), e);
    }
    Set<String> ids = new HashSet<>();
    for (Pedra pedra : catalogo.pedras) {
      if (!ids.add(pedra.id())) {
        throw new IllegalStateException("Id duplicado no catalogo: " + pedra.id());
      }
      if (!Files.exists(diretorio.resolve(pedra.arquivo()))) {
        throw new IllegalStateException(
            "Imagem da pedra " + pedra.id() + " nao encontrada: " + pedra.arquivo());
      }
    }
    return catalogo.pedras;
  }

  /** Formato de {@code catalogo.json}. */
  private record CatalogoJson(List<Pedra> pedras) {}
}
