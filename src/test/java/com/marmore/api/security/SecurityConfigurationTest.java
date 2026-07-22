package com.marmore.api.security;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes de integracao da {@link SecurityConfiguration}. Valida o contrato de autenticacao por API
 * key end-to-end, sem bypassar a security (sem addFilters=false).
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMockRestServiceServer
@TestPropertySource(
    properties = {
      "marmore.api.key=segredo-teste",
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-openai-teste",
      "marmore.openai.image.timeout=5s"
    })
class SecurityConfigurationTest {

  @Autowired MockMvc mockMvc;
  @Autowired MockRestServiceServer server;
  @Autowired ApiKeyProperties props;

  private static final byte[] PEDRA_BYTES = carregarPedra();

  private static byte[] carregarPedra() {
    try {
      return new ClassPathResource("test-images/ambiente.png").getInputStream().readAllBytes();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @BeforeEach
  void resetEstado() {
    props.setKey("segredo-teste");
    server.reset();
  }

  /** Sem header X-API-Key: 401 (nao chega ao controller). */
  @Test
  void semApiKeyRetorna401() throws Exception {
    MockMultipartFile imagem =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc.perform(multipart("/images/edit").file(imagem)).andExpect(status().isUnauthorized());
  }

  /** Header X-API-Key invalido: 401. */
  @Test
  void apiKeyInvalidaRetorna401() throws Exception {
    MockMultipartFile imagem =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc
        .perform(multipart("/images/edit").file(imagem).header(ApiKeyAuthFilter.HEADER, "errada"))
        .andExpect(status().isUnauthorized());
  }

  /**
   * Header X-API-Key valido: passa pela security e chega ao controller (aqui 200 com OpenAI mock).
   */
  @Test
  void apiKeyValidaChegaAoController() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(
            withSuccess("{\"data\":[{\"b64_json\":\"aGVsbG8=\"}]}", MediaType.APPLICATION_JSON));
    MockMultipartFile imagem =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc
        .perform(
            multipart("/images/edit").file(imagem).header(ApiKeyAuthFilter.HEADER, "segredo-teste"))
        .andExpect(status().isOk());
  }
}
