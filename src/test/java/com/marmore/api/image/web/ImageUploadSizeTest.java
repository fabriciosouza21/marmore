package com.marmore.api.image.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Teste de limite de upload (HTTP real): POST /images/edit com payload maior que 25MB deve devolver
 * 413 Payload Too Large.
 *
 * <p>Usa HTTP real (TestRestTemplate em RANDOM_PORT) porque o MockMvc nao aciona o resolvedor
 * multipart do servlet; o {@code MockMultipartFile} entra no controller sem passar pelo filtro de
 * tamanho, e o teste nunca exercitaria a validacao.
 *
 * <p>Descoberta TDD: no Spring Boot 4.1 / Tomcat 11, o 413 e produzido <strong>nativamente</strong>
 * pelo Tomcat durante {@code Request.parseParts()}, antes de o controle chegar ao
 * DispatcherServlet. Por isso um {@code @RestControllerAdvice} para {@code
 * MaxUploadSizeExceededException} nunca seria acionado - a resposta ja esta commitada quando o
 * Spring MVC entra em cena. Nao foi necessario adicionar handler algum.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(
    properties = {
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-teste",
      "marmore.openai.image.timeout=5s"
    })
class ImageUploadSizeTest {

  @Autowired TestRestTemplate restTemplate;

  /**
   * Upload de 26MB (acima do limite de 25MB): devolve 413 Payload Too Large.
   *
   * <p>A verificacao usa {@code value()} (413) e nao o enum {@code HttpStatus.PAYLOAD_TOO_LARGE}
   * porque no Spring 7 esse nome foi descontinuado em favor de {@code CONTENT_TOO_LARGE} (RFC
   * 9110); ambos valem 413. O contrato de interesse e o codigo HTTP.
   */
  @Test
  void postDeveRetornar413QuandoUploadExcede25Mb() {
    byte[] grande = new byte[26 * 1024 * 1024];
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add(
        "image",
        new ByteArrayResource(grande) {
          @Override
          public String getFilename() {
            return "grande.png";
          }
        });

    ResponseEntity<String> resposta =
        restTemplate.postForEntity("/images/edit", body, String.class);

    assertEquals(413, resposta.getStatusCode().value());
  }
}
