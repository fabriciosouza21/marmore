package com.marmore.api.imageedit.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuracao do modulo de edicao de imagem da OpenAI. Prefixo: {@code
 * marmore.openai.image}.
 */
@ConfigurationProperties(prefix = "marmore.openai.image")
public class ImageEditProperties {

  private String baseUrl = "https://api.openai.com";
  private String apiKey;
  private String defaultModel = "gpt-image-2";
  private Duration timeout = Duration.ofSeconds(180);
  private Path stonePath;

  /** Retorna a URL base da API. */
  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  /** Retorna a chave de API. */
  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  /** Retorna o modelo padrao. */
  public String getDefaultModel() {
    return defaultModel;
  }

  public void setDefaultModel(String defaultModel) {
    this.defaultModel = defaultModel;
  }

  /** Retorna o timeout da chamada HTTP. */
  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }

  /** Retorna o caminho do arquivo da pedra (granito) enviado como IMAGE 2. */
  public Path getStonePath() {
    return stonePath;
  }

  public void setStonePath(Path stonePath) {
    this.stonePath = stonePath;
  }
}
