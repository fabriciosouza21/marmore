package com.marmore.api.image.web;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marmore.api.image.config.ImageEditProperties;
import com.marmore.api.security.ApiKeyAuthFilter;
import java.nio.file.Path;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Testes de {@link ImageEditController}. A security real atua: header X-API-Key e exigido. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMockRestServiceServer
@TestPropertySource(
    properties = {
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-teste",
      "marmore.openai.image.timeout=5s",
      "marmore.api.key=chave-teste-fixa"
    })
class ImageEditControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired MockRestServiceServer server;
  @Autowired ImageEditProperties props;

  private static final Path PEDRA_VALIDA;
  private static final byte[] PEDRA_BYTES;

  static {
    try {
      PEDRA_VALIDA = new ClassPathResource("test-images/ambiente.png").getFile().toPath();
      PEDRA_BYTES =
          new ClassPathResource("test-images/ambiente.png").getInputStream().readAllBytes();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @org.junit.jupiter.api.BeforeEach
  void resetEstado() {
    props.setApiKey("chave-teste");
    props.setStonePath(PEDRA_VALIDA);
    server.reset();
  }

  /** Monta o multipart com o header X-API-Key (security real ativa). */
  private MockMultipartHttpServletRequestBuilder reqAutenticado() {
    return multipart("/images/edit").header(ApiKeyAuthFilter.HEADER, "chave-teste-fixa");
  }

  /** Sucesso: POST /images/edit devolve 200 com Content-Type image/png. */
  @Test
  void postDeveRetornarPngQuandoServidorOpenAiRespondeB64() throws Exception {
    byte[] imagemEsperada = java.util.Base64.getDecoder().decode("aGVsbG8=");
    String corpo = "{\"data\":[{\"b64_json\":\"aGVsbG8=\"}]}";
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc
        .perform(reqAutenticado().file(imagemPart))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(content().bytes(imagemEsperada));
  }

  /** Api-key ausente: POST /images/edit devolve 503. */
  @Test
  void postDeveRetornar503QuandoApiKeyVazia() throws Exception {
    props.setApiKey("");

    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc.perform(reqAutenticado().file(imagemPart)).andExpect(status().isServiceUnavailable());
  }

  /** Pedra ausente: POST /images/edit devolve 503. */
  @Test
  void postDeveRetornar503QuandoPedraAusente() throws Exception {
    props.setStonePath(java.nio.file.Paths.get("/tmp/nao-existe.png"));

    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc.perform(reqAutenticado().file(imagemPart)).andExpect(status().isServiceUnavailable());
  }

  /** Erro HTTP da OpenAI: POST /images/edit devolve 502. */
  @Test
  void postDeveRetornar502QuandoServidorOpenAiRespondeErro() throws Exception {
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(
            org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest()
                .body("{\"error\":{\"message\":\"bad model\"}}"));
    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc.perform(reqAutenticado().file(imagemPart)).andExpect(status().isBadGateway());
  }

  /** Imagem indecodificavel: POST /images/edit devolve 400 sem chamar a OpenAI. */
  @Test
  void postDeveRetornar400QuandoImagemIndecodificavel() throws Exception {
    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", new byte[] {1, 2, 3, 4});

    mockMvc.perform(reqAutenticado().file(imagemPart)).andExpect(status().isBadRequest());
  }
}
