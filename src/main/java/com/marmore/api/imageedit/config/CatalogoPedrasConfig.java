package com.marmore.api.imageedit.config;

import com.marmore.api.imageedit.domain.CatalogoPedras;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring do {@link CatalogoPedras}: carrega e valida o catalogo do diretorio de pedras na
 * inicializacao (fail-fast: diretorio, JSON, ids duplicados ou imagens ausentes derrubam o boot).
 */
@Configuration
public class CatalogoPedrasConfig {

  /**
   * Cria o bean do catalogo a partir da propriedade {@code marmore.openai.image.pedras-path}.
   *
   * @param props propriedades do modulo de edicao de imagem
   * @return catalogo carregado e validado
   */
  @Bean
  public CatalogoPedras catalogoPedras(ImageEditProperties props) {
    return new CatalogoPedras(props.getPedrasPath());
  }
}
