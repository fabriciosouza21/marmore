package com.marmore.api.image.io;

import com.marmore.api.image.domain.GenerateResult;
import java.nio.file.Path;

/** Estrategia de persistencia de um {@link GenerateResult}. Separa a chamada da API da gravacao. */
public interface ImageResultWriter {

  /**
   * Persiste o resultado em {@code dir} com prefixo {@code name}.
   *
   * @param result resultado de generate
   * @param dir diretorio de saida (criado se necessario)
   * @param name prefixo dos arquivos ({@code <name>.png}, {@code <name>.response.json})
   * @return caminhos gravados, ou nulos quando nao aplicavel
   */
  WriteResult write(GenerateResult result, Path dir, String name);

  /** Caminhos dos arquivos gravados. */
  record WriteResult(Path image, Path response) {}
}
