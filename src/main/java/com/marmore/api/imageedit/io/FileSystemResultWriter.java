package com.marmore.api.imageedit.io;

import com.marmore.api.imageedit.domain.GenerateResult;
import com.marmore.api.imageedit.domain.GenerateResult.Err;
import com.marmore.api.imageedit.domain.GenerateResult.Ok;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

/**
 * Persistencia de {@link GenerateResult} em filesystem. Cria o diretorio se necessario. Em caso de
 * {@link Err}, lanca {@link IllegalStateException}. Nao chama a API.
 */
@Component
public class FileSystemResultWriter implements ImageResultWriter {

  private final ObjectWriter jsonWriter;

  /**
   * Construtor.
   *
   * @param mapper Jackson ObjectMapper para gravar o JSON cru
   */
  public FileSystemResultWriter(ObjectMapper mapper) {
    this.jsonWriter = mapper.writerWithDefaultPrettyPrinter();
  }

  @Override
  public WriteResult write(GenerateResult result, Path dir, String name) {
    if (result instanceof Err err) {
      throw new IllegalStateException("resultado de erro: " + err.error());
    }
    Ok ok = (Ok) result;
    try {
      Files.createDirectories(dir);
      Path image = dir.resolve(name + ".png");
      Files.write(image, Base64.getDecoder().decode(ok.b64()));

      Path response = null;
      if (ok.raw() != null) {
        response = dir.resolve(name + ".response.json");
        Files.writeString(response, jsonWriter.writeValueAsString(ok.raw()));
      }
      return new WriteResult(image, response);
    } catch (IOException e) {
      throw new IllegalStateException("falha ao gravar resultado: " + e.getMessage(), e);
    }
  }
}
