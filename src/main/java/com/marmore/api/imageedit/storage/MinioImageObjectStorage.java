package com.marmore.api.imageedit.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Implementacao do {@link ImageObjectStorage} sobre MinIO (API S3). Cada imagem e gravada com key
 * aleatoria {@code <uuid>.png}; o bucket deve existir previamente (criado no compose). O SDK e
 * bloqueante: cada operacao roda em {@link Schedulers#boundedElastic()} e falhas sobem como erro do
 * {@link Mono}.
 */
@Component
public class MinioImageObjectStorage implements ImageObjectStorage {

  private static final String CONTENT_TYPE_PNG = "image/png";

  private final MinioClient client;
  private final String bucket;

  /** Cria o cliente MinIO a partir das propriedades de storage. */
  public MinioImageObjectStorage(StorageProperties props) {
    this.client =
        MinioClient.builder()
            .endpoint(props.getEndpoint())
            .credentials(props.getAccessKey(), props.getSecretKey())
            .build();
    this.bucket = props.getBucket();
  }

  @Override
  public Mono<String> salvar(byte[] conteudo) {
    return Mono.fromCallable(
            () -> {
              String objetoKey = UUID.randomUUID() + ".png";
              client.putObject(
                  PutObjectArgs.builder()
                      .bucket(bucket)
                      .object(objetoKey)
                      .contentType(CONTENT_TYPE_PNG)
                      .stream(new ByteArrayInputStream(conteudo), conteudo.length, -1)
                      .build());
              return objetoKey;
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  @Override
  public Mono<byte[]> baixar(String objetoKey) {
    return Mono.fromCallable(
            () ->
                client
                    .getObject(GetObjectArgs.builder().bucket(bucket).object(objetoKey).build())
                    .readAllBytes())
        .subscribeOn(Schedulers.boundedElastic());
  }
}
