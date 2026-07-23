package com.marmore.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marmore.api.imageedit.config.ImageEditProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes de integracao da {@link SecurityConfiguration}. Valida o contrato de autenticacao por API
 * key end-to-end, sem bypassar a security (sem addFilters=false).
 *
 * <p>O endpoint /images/edit agora e um {@code RouterFunction} reativo (WebFlux - Task 12), que o
 * DispatcherServlet do Spring MVC nao atende; por isso o cenario de API key valida nao produz 200
 * aqui (cai no ResourceHttpRequestHandler -&gt; 404). O que se verifica e o contrato da security
 * (header valido passa, ausente/invalido vira 401). O fluxo SSE feliz e cobrado isoladamente em
 * {@code ImageEditHandlerTest}.
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
      "Header X-API-Key valido: passa pela security (nao e 401; rota reativa vive fora do MockMvc)")
  @Test
  void apiKeyValidaPassaPelaSecurity() throws Exception {
    MockMultipartFile imagem =
        new MockMultipartFile("image", "ambiente.png", "image/png", PEDRA_BYTES);

    // O endpoint /images/edit agora e um RouterFunction reativo (WebFlux), que o DispatcherServlet
    // do Spring MVC (usado pelo MockMvc) nao atende: a requisicao avanca ate cair no
    // ResourceHttpRequestHandler (404 NoResourceFoundException). O que importa aqui e que a
    // security deixou passar - ou seja, qualquer status diferente de 401 prova que o header
    // valido autenticou. O fluxo SSE feliz e cobrado isoladamente em ImageEditHandlerTest.
    mockMvc
        .perform(
            multipart("/images/edit").file(imagem).header(ApiKeyAuthFilter.HEADER, "segredo-teste"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
  }
}
