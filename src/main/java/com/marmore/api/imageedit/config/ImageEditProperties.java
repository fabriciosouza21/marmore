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
 * stone-path/pedras-path estiverem nulos, o contexto falha (fail-fast). A existencia do arquivo da
 * pedra em disco e validada por uso, no {@code ImageEditService}.
 */
@ConfigurationProperties(prefix = "marmore.openai.image")
public class ImageEditProperties {

  private String baseUrl = "https://api.openai.com";
  private String apiKey;
  private String defaultModel = ImageModel.GPT_IMAGE_2.apiValue();
  private Duration timeout = Duration.ofSeconds(180);
  private Path stonePath;
  private Path pedrasPath;

  /** Valida na inicializacao que a chave, o stone-path e o pedras-path estao definidos. */
  @PostConstruct
  void validar() {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "marmore.openai.image.api-key ausente. Defina OPENAI_API_KEY no ambiente.");
    }
    if (stonePath == null) {
      throw new IllegalStateException("marmore.openai.image.stone-path ausente.");
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

  /** Retorna o caminho do arquivo da pedra (granito) enviado como IMAGE 2. */
  public Path getStonePath() {
    return stonePath;
  }

  public void setStonePath(Path stonePath) {
    this.stonePath = stonePath;
  }

  /** Retorna o diretorio do catalogo de pedras. */
  public Path getPedrasPath() {
    return pedrasPath;
  }

  public void setPedrasPath(Path pedrasPath) {
    this.pedrasPath = pedrasPath;
  }
}
