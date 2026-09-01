package com.marmore.api.imageedit.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Teste de integracao dos endpoints REST do catalogo de pedras: {@code GET /pedras} (JSON array na
 * ordem do catalogo, sem o campo interno {@code arquivo}) e {@code GET /pedras/{id}/imagem} (bytes
 * com Content-Type conforme a extensao). Como todo o resto, exceto {@code /health}, exige o header
 * {@code X-API-Key}; sem ele a resposta e 401.
 *
 * <p>Sobe o contexto WebFlux completo com servidor embutido e aponta {@code
 * marmore.openai.image.pedras-path} para {@code src/test/resources/pedras-teste} via {@link
 * DynamicPropertySource} (caminho absoluto em runtime), mantendo o catalogo de producao intocado. O
 * {@link WebTestClient} e construido manualmente apontando para a porta aleatoria (Spring Boot 4.1
 * nao registra mais o bean automaticamente).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PedrasIntegrationTest {

  /** API key valida configurada em {@code src/test/resources/application.yaml}. */
  private static final String API_KEY_VALIDA = "chave-teste-fixa";

  /** Diretorio do catalogo de teste, resolvido como caminho absoluto em runtime. */
  private static final Path DIR_PEDRAS =
      Paths.get("src/test/resources/pedras-teste").toAbsolutePath();

  /** Mapper Jackson 3 para as assercoes de corpo JSON. */
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @LocalServerPort private int porta;

  /** Aponta o catalogo de pedras para o diretorio de recursos de teste. */
  @DynamicPropertySource
  static void apontaCatalogoDeTeste(DynamicPropertyRegistry registro) {
    registro.add("marmore.openai.image.pedras-path", () -> DIR_PEDRAS.toString());
  }

  /** Constroi o {@link WebTestClient} apontando para o servidor embutido na porta aleatoria. */
  private WebTestClient cliente() {
    return WebTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
  }

  @DisplayName("GET /pedras com X-API-Key: catalogo na ordem, sem o campo arquivo")
  @Test
  void listaPedrasNaOrdemSemCampoArquivo() {
    String corpo =
        cliente()
            .get()
            .uri("/pedras")
            .header("X-API-Key", API_KEY_VALIDA)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode raiz = MAPPER.readTree(corpo);
    assertThat(raiz.size()).isEqualTo(2);

    JsonNode verde = raiz.get(0);
    assertThat(verde.get("id").asText()).isEqualTo("verde_ubatuba");
    assertThat(verde.get("nome").asText()).isEqualTo("Verde Ubatuba");
    assertThat(verde.get("categoria").asText()).isEqualTo("Granitos");
    assertThat(verde.has("arquivo")).isFalse();

    JsonNode calacatta = raiz.get(1);
    assertThat(calacatta.get("id").asText()).isEqualTo("calacatta");
    assertThat(calacatta.get("nome").asText()).isEqualTo("Calacatta");
    assertThat(calacatta.get("categoria").asText()).isEqualTo("Marmores");
    assertThat(calacatta.has("arquivo")).isFalse();
  }

  @DisplayName("GET /pedras sem X-API-Key: 401 Unauthorized")
  @Test
  void semApiKeyRetorna401() {
    cliente().get().uri("/pedras").exchange().expectStatus().isUnauthorized();
  }

  @DisplayName("GET /pedras/{id}/imagem de pedra .png: bytes do arquivo e image/png")
  @Test
  void imagemPngRetornaBytesComContentTypePng() throws Exception {
    byte[] esperado = Files.readAllBytes(DIR_PEDRAS.resolve("verde_ubatuba.png"));

    byte[] corpo =
        cliente()
            .get()
            .uri("/pedras/verde_ubatuba/imagem")
            .header("X-API-Key", API_KEY_VALIDA)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.IMAGE_PNG)
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

    assertThat(corpo).isEqualTo(esperado);
  }

  @DisplayName("GET /pedras/{id}/imagem de pedra .jpeg: image/jpeg")
  @Test
  void imagemJpegRetornaContentTypeJpeg() {
    cliente()
        .get()
        .uri("/pedras/calacatta/imagem")
        .header("X-API-Key", API_KEY_VALIDA)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.IMAGE_JPEG);
  }

  @DisplayName("GET /pedras/{id}/imagem com id inexistente: 404 com corpo de erro")
  @Test
  void idInexistenteRetorna404ComCorpoDeErro() {
    String corpo =
        cliente()
            .get()
            .uri("/pedras/id-inexistente/imagem")
            .header("X-API-Key", API_KEY_VALIDA)
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode erro = MAPPER.readTree(corpo);
    assertThat(erro.has("error")).isTrue();
    assertThat(erro.get("error").asText()).isNotBlank();
  }
}
