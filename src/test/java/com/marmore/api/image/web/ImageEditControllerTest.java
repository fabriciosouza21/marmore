package com.marmore.api.image.web;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

/** Testes de {@link ImageEditController}. */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureMockRestServiceServer
@TestPropertySource(
    properties = {
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-teste",
      "marmore.openai.image.timeout=5s"
    })
class ImageEditControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired MockRestServiceServer server;

  /** Sucesso: POST /images/edit devolve 200 com Content-Type image/png. */
  @Test
  void postDeveRetornarPngQuandoServidorOpenAiRespondeB64() throws Exception {
    byte[] imagemEsperada = java.util.Base64.getDecoder().decode("aGVsbG8=");
    String corpo = "{\"data\":[{\"b64_json\":\"aGVsbG8=\"}]}";
    server
        .expect(requestTo("https://example.test/v1/images/edits"))
        .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

    MockMultipartFile imagemPart =
        new MockMultipartFile("images", "ambiente.png", "image/png", new byte[] {1, 2, 3});

    mockMvc
        .perform(multipart("/images/edit").file(imagemPart).param("prompt", "bancada verde"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(content().bytes(imagemEsperada));
  }
}
