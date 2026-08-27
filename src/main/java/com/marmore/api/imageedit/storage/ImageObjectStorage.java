package com.marmore.api.imageedit.storage;

import reactor.core.publisher.Mono;

/**
 * Contrato do armazenamento de objetos das imagens geradas. Operacoes reativas por fora: o I/O
 * bloqueante fica dentro da implementacao (boundedElastic), sem prender o event loop.
 */
public interface ImageObjectStorage {

  /**
   * Envia os bytes da imagem ao object storage.
   *
   * @param conteudo bytes da imagem (PNG)
   * @return {@link Mono} com a key do objeto gravado
   */
  Mono<String> salvar(byte[] conteudo);

  /**
   * Baixa os bytes de um objeto previamente gravado.
   *
   * @param objetoKey key do objeto no storage
   * @return {@link Mono} com os bytes do objeto
   */
  Mono<byte[]> baixar(String objetoKey);
}
