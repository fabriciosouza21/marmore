package com.marmore.api.security;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marmore.api.imageedit.ai.Image;
import com.marmore.api.imageedit.ai.ImageEditModel;
import com.marmore.api.imageedit.ai.ImageGeneration;
import com.marmore.api.imageedit.ai.ImageResponse;
import com.marmore.api.imageedit.ai.ImageResponseMetadata;
import com.marmore.api.imageedit.config.ImageEditProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes de integracao da {@link SecurityConfiguration}. Valida o contrato de autenticacao por API
 * key end-to-end, sem bypassar a security (sem addFilters=false).
 *
 * <p>O gateway reativo ({@link ImageEditModel}) e substituido por {@link MockitoBean mock}: o foco
 * aqui e a security, nao o transporte HTTP (a migracao RestClient->WebClient em Tasks 9-10 tornou o
 * antigo {@code MockRestServiceServer} inaplicavel; o contrato do gateway e cobrado isoladamente em
 * {@code OpenAiWebClientImageEditModelTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "marmore.api.key=segredo-teste",
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-openai-teste",
      "marmore.openai.image.timeout=5s"
    })
class SecurityConfigurationTest {

  @Autowired MockMvc mockMvc;
  @Autowired ApiKeyProperties props;
  @Autowired ImageEditProperties imageProps;

  @MockitoBean ImageEditModel model;

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
    imageProps.setApiKey("chave-openai-teste");
    imageProps.setStonePath(pedraValida());
  }

  private static java.nio.file.Path pedraValida() {
    try {
      return new ClassPathResource("test-images/ambiente.png").getFile().toPath();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @org.junit.jupiter.api.DisplayName("Sem header X-API-Key: 401 (nao chega ao controller)")
  @Test
  void semApiKeyRetorna401() throws Exception {
    MockMultipartFile imagem =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc.perform(multipart("/images/edit").file(imagem)).andExpect(status().isUnauthorized());
  }

  @org.junit.jupiter.api.DisplayName("Header X-API-Key invalido: 401")
  @Test
  void apiKeyInvalidaRetorna401() throws Exception {
    MockMultipartFile imagem =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc
        .perform(multipart("/images/edit").file(imagem).header(ApiKeyAuthFilter.HEADER, "errada"))
        .andExpect(status().isUnauthorized());
  }

  @org.junit.jupiter.api.DisplayName(
      "Header X-API-Key valido: passa pela security e chega ao controller (200 com gateway mock)")
  @Test
  void apiKeyValidaChegaAoController() throws Exception {
    org.mockito.Mockito.when(model.call(any()))
        .thenReturn(
            reactor.core.publisher.Mono.just(
                new ImageResponse(
                    List.of(ImageGeneration.of(Image.of("aGVsbG8="))),
                    ImageResponseMetadata.empty(),
                    null)));
    MockMultipartFile imagem =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    mockMvc
        .perform(
            multipart("/images/edit").file(imagem).header(ApiKeyAuthFilter.HEADER, "segredo-teste"))
        .andExpect(status().isOk());
  }
}
