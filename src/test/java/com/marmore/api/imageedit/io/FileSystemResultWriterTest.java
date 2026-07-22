package com.marmore.api.imageedit.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marmore.api.imageedit.domain.GenerateResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** Testes de {@link FileSystemResultWriter}. */
class FileSystemResultWriterTest {

  private final FileSystemResultWriter writer = new FileSystemResultWriter(new ObjectMapper());
  private final ObjectMapper mapper = new ObjectMapper();

  @TempDir Path temp;

  @Test
  @DisplayName("Caso #10: sucesso com JSON bruto grava imagem e resposta, bytes decodificados")
  void escreveImagemEhRespostaQuandoResultadoDeSucessoComBruto() throws IOException {
    String b64 = java.util.Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
    var raw = mapper.readTree("{\"data\":[{\"b64_json\":\"" + b64 + "\"}]}");
    GenerateResult ok = new GenerateResult.Ok(b64, raw, null, 10L);

    ImageResultWriter.WriteResult result = writer.write(ok, temp, "bancada");

    assertThat(result.image()).isEqualTo(temp.resolve("bancada.png"));
    assertThat(result.response()).isEqualTo(temp.resolve("bancada.response.json"));
    assertThat(Files.readAllBytes(result.image())).containsExactly(1, 2, 3);
    assertThat(Files.readString(result.response())).contains("b64_json");
  }

  @Test
  @DisplayName("Caso #12: Err deve lancar IllegalStateException")
  void lancaIllegalStateQuandoResultadoErro() {
    GenerateResult err = new GenerateResult.Err("falhou", 5L);

    assertThatThrownBy(() -> writer.write(err, temp, "x"))
        .isInstanceOf(IllegalStateException.class);
  }
}
