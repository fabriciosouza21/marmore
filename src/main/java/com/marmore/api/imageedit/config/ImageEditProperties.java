package com.marmore.api.imageedit.config;

import com.marmore.api.imageedit.model.ImageModel;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuracao do modulo de edicao de imagem da OpenAI. Prefixo: {@code
 * marmore.openai.image}.
 *
 * <p>Validacao na inicializacao ({@link #validar()}): se a API key estiver ausente/em branco ou o
 * pedras-path estiver nulo, o contexto falha (fail-fast). A existencia dos arquivos das pedras em
 * disco e validada no carregamento do catalogo.
 */
@ConfigurationProperties(prefix = "marmore.openai.image")
public class ImageEditProperties {

  private String baseUrl = "https://api.openai.com";
  private String apiKey;
  private String defaultModel = ImageModel.GPT_IMAGE_2.apiValue();
  private Duration timeout = Duration.ofSeconds(180);
  private Path pedrasPath;

  /** Valida na inicializacao que a chave e o pedras-path estao definidos. */
  @PostConstruct
  void validar() {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "marmore.openai.image.api-key ausente. Defina OPENAI_API_KEY no ambiente.");
    }
    if (pedrasPath == null) {
      throw new IllegalStateException("marmore.openai.image.pedras-path ausente.");
    }
  }

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

  /** Retorna o diretorio do catalogo de pedras. */
  public Path getPedrasPath() {
    return pedrasPath;
  }

  public void setPedrasPath(Path pedrasPath) {
    this.pedrasPath = pedrasPath;
  }
}
