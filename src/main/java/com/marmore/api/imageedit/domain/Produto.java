package com.marmore.api.imageedit.domain;

/**
 * Produto do catalogo: a identidade do que esta sendo gerado. Hoje existe apenas a pia americana;
 * novos produtos entram como novas constantes aqui, cada uma com seu id estavel (gravado nos
 * metadados das imagens geradas) e seu nome de exibicao.
 */
public enum Produto {

  /** Pia americana: unico produto disponivel no catalogo atualmente. */
  PIA_AMERICANA("pia-americana", "Pia americana");

  private final String id;
  private final String nomeExibicao;

  Produto(String id, String nomeExibicao) {
    this.id = id;
    this.nomeExibicao = nomeExibicao;
  }

  /** Id estavel do produto, gravado nos metadados das imagens geradas. */
  public String id() {
    return id;
  }

  /** Nome comercial para exibicao na interface. */
  public String nomeExibicao() {
    return nomeExibicao;
  }
}
