package com.marmore.api.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades da API key de acesso. Prefixo: {@code marmore.api}. A chave e obrigatoria: se
 * ausente em branco na inicializacao, o contexto falha (nao sobe insegura).
 */
@ConfigurationProperties(prefix = "marmore.api")
public class ApiKeyProperties {

  private String key;

  /** Valida na inicializacao que a chave esta definida. */
  @PostConstruct
  void validar() {
    if (key == null || key.isBlank()) {
      throw new IllegalStateException(
          "marmore.api.key ausente. Defina MARMORE_API_KEY no ambiente.");
    }
  }

  /** Retorna a chave de API configurada. */
  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }
}
