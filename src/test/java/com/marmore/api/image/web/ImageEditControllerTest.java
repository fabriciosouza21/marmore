package com.marmore.api.image.web;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marmore.api.imageedit.ai.AiImageException;
import com.marmore.api.imageedit.ai.Image;
import com.marmore.api.imageedit.ai.ImageEditModel;
import com.marmore.api.imageedit.ai.ImageGeneration;
import com.marmore.api.imageedit.ai.ImageResponse;
import com.marmore.api.imageedit.ai.ImageResponseMetadata;
import com.marmore.api.imageedit.config.ImageEditProperties;
import com.marmore.api.security.ApiKeyAuthFilter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Testes de {@link ImageEditController}. A security real atua: header X-API-Key e exigido.
 *
 * <p>O gateway reativo ({@link ImageEditModel}) e substituido por {@link MockitoBean mock}: o foco
 * aqui e o comportamento do controller (status/ContentType por cenario de {@link
 * com.marmore.api.imageedit.domain.GenerateResult}), nao o transporte HTTP. A migracao
 * RestClient->WebClient (Tasks 9-10) tornou o antigo {@code MockRestServiceServer} inaplicavel; o
 * contrato do gateway (incluindo o SSE) e cobrado isoladamente em {@code
 * OpenAiWebClientImageEditModelTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-teste",
      "marmore.openai.image.timeout=5s",
      "marmore.api.key=chave-teste-fixa"
    })
class ImageEditControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ImageEditProperties props;

  @MockitoBean ImageEditModel model;

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

  @BeforeEach
  void resetEstado() {
    props.setApiKey("chave-teste");
    props.setStonePath(PEDRA_VALIDA);
    org.mockito.Mockito.reset(model);
  }

  private MockMultipartHttpServletRequestBuilder reqAutenticado() {
    return multipart("/images/edit").header(ApiKeyAuthFilter.HEADER, "chave-teste-fixa");
  }

  @DisplayName("Sucesso: POST /images/edit devolve 200 com Content-Type image/png")
  @Test
  void postDeveRetornarPngQuandoServidorOpenAiRespondeB64() throws Exception {
    byte[] imagemEsperada = java.util.Base64.getDecoder().decode("aGVsbG8=");
    org.mockito.Mockito.when(model.call(any()))
        .thenReturn(
            reactor.core.publisher.Mono.just(
                new ImageResponse(
                    List.of(ImageGeneration.of(Image.of("aGVsbG8="))),
                    ImageResponseMetadata.empty(),
                    null)));

    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc
        .perform(reqAutenticado().file(imagemPart))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(content().bytes(imagemEsperada));
  }

  @DisplayName("Api-key ausente: POST /images/edit devolve 503")
  @Test
  void postDeveRetornar503QuandoApiKeyVazia() throws Exception {
    props.setApiKey("");

    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc.perform(reqAutenticado().file(imagemPart)).andExpect(status().isServiceUnavailable());
  }

  @DisplayName("Pedra ausente: POST /images/edit devolve 503")
  @Test
  void postDeveRetornar503QuandoPedraAusente() throws Exception {
    props.setStonePath(java.nio.file.Paths.get("/tmp/nao-existe.png"));

    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc.perform(reqAutenticado().file(imagemPart)).andExpect(status().isServiceUnavailable());
  }

  @DisplayName("Erro do gateway (AiImageException): POST /images/edit devolve 502")
  @Test
  void postDeveRetornar502QuandoServidorOpenAiRespondeErro() throws Exception {
    org.mockito.Mockito.when(model.call(any()))
        .thenReturn(reactor.core.publisher.Mono.error(new AiImageException("[400]: bad model")));
    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc.perform(reqAutenticado().file(imagemPart)).andExpect(status().isBadGateway());
  }

  @DisplayName("Imagem indecodificavel: POST /images/edit devolve 400 sem chamar o gateway")
  @Test
  void postDeveRetornar400QuandoImagemIndecodificavel() throws Exception {
    MockMultipartFile imagemPart =
        new MockMultipartFile("image", "ambiente.png", "image/png", new byte[] {1, 2, 3, 4});

    mockMvc.perform(reqAutenticado().file(imagemPart)).andExpect(status().isBadRequest());
  }
}
