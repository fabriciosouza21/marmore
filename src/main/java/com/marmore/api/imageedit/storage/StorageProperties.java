package com.marmore.api.imageedit.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do armazenamento de objetos (MinIO) das imagens geradas. Prefixo: {@code
 * marmore.storage}. Todas as propriedades sao obrigatorias: se alguma estiver ausente ou em branco
 * na inicializacao, o contexto falha (nao sobe sem destino de persistencia definido).
 */
@ConfigurationProperties(prefix = "marmore.storage")
public class StorageProperties {

  private String endpoint;
  private String accessKey;
  private String secretKey;
  private String bucket;

  /** Valida na inicializacao que as propriedades estao definidas. */
  @PostConstruct
  void validar() {
    if (emBranco(endpoint) || emBranco(accessKey) || emBranco(secretKey) || emBranco(bucket)) {
      throw new IllegalStateException(
          "marmore.storage incompleto (endpoint, access-key, secret-key, bucket). Defina"
              + " MINIO_ENDPOINT, MINIO_ROOT_USER, MINIO_ROOT_PASSWORD e MINIO_BUCKET no"
              + " ambiente.");
    }
  }

  private static boolean emBranco(String valor) {
    return valor == null || valor.isBlank();
  }

  /** Retorna a URL do endpoint S3 (ex.: http://localhost:9000). */
  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  /** Retorna a access key (MINIO_ROOT_USER no compose local). */
  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  /** Retorna a secret key (MINIO_ROOT_PASSWORD no compose local). */
  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  /** Retorna o nome unico do bucket das imagens geradas. */
  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }
}
