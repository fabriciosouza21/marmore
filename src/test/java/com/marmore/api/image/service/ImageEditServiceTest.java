package com.marmore.api.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.marmore.api.image.ai.AiImageException;
import com.marmore.api.image.ai.AiImageOptions;
import com.marmore.api.image.ai.Image;
import com.marmore.api.image.ai.ImageEditModel;
import com.marmore.api.image.ai.ImageEditPrompt;
import com.marmore.api.image.ai.ImageGeneration;
import com.marmore.api.image.ai.ImageResponse;
import com.marmore.api.image.ai.ImageResponseMetadata;
import com.marmore.api.image.config.ImageEditProperties;
import com.marmore.api.image.domain.EditPrompts;
import com.marmore.api.image.domain.GenerateResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Testes de {@link ImageEditService}. O foco e o papel do service: validacoes pre-rede e traducao
 * entre o contrato do gateway (lanca em falha) e {@link GenerateResult} (Ok/Err). O gateway real e
 * substituido por um mock ({@code @MockitoBean}).
 */
@SpringBootTest(
    properties = {
      "marmore.openai.image.base-url=https://example.test",
      "marmore.openai.image.api-key=chave-teste",
      "marmore.openai.image.timeout=5s"
    })
class ImageEditServiceTest {

  @Autowired ImageEditService service;
  @Autowired ImageEditProperties props;
  @MockitoBean ImageEditModel model;

  /** Caminho da pedra de teste (ambiente.png do classpath), resolvido em runtime. */
  private static Path pedraValida() {
    try {
      return new ClassPathResource("test-images/ambiente.png").getFile().toPath();
    } catch (java.io.IOException e) {
      throw new IllegalStateException("ambiente.png ausente do classpath", e);
    }
  }

  @BeforeEach
  void resetEstado() {
    props.setApiKey("chave-teste");
    props.setStonePath(pedraValida());
  }

  /** Caso #2: api-key vazia deve retornar Err sem chamar o gateway. */
  @Test
  void erroQuandoApiKeyVaziaSemChamarGateway() throws Exception {
    props.setApiKey("");

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("OPENAI_API_KEY ausente");
  }

  /** Caso #3: pedra (stone-path) inexistente deve retornar Err sem chamar o gateway. */
  @Test
  void erroQuandoPedraAusenteSemChamarGateway() throws Exception {
    props.setStonePath(Paths.get("/tmp/arquivo-que-nao-existe.png"));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("stone image not found");
  }

  /** Caso #3b: ambiente indecodificavel deve retornar Err sem chamar o gateway. */
  @Test
  void erroQuandoImagemIndecodificavelSemChamarGateway() {
    GenerateResult result = service.generate(new byte[] {1, 2, 3, 4});

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("unable to decode input image");
  }

  /** Caso #1: gateway devolve b64 com usage -> Ok com b64 e usage propagados. */
  @Test
  void sucessoQuandoGatewayDevolveB64() throws Exception {
    when(model.call(any())).thenReturn(respostaComB64("aGVsbG8=", true));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    assertThat(((GenerateResult.Ok) result).b64()).isEqualTo("aGVsbG8=");
    assertThat(((GenerateResult.Ok) result).usage()).isNotNull();
    assertThat(((GenerateResult.Ok) result).raw()).isNotNull();
  }

  /** Caso #7: gateway devolve b64 sem usage -> Ok com usage nulo. */
  @Test
  void sucessoQuandoGatewayDevolveB64SemUsage() throws Exception {
    when(model.call(any())).thenReturn(respostaComB64("eA==", false));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    assertThat(((GenerateResult.Ok) result).usage()).isNull();
  }

  /** Caso #6: gateway lanca AiImageException -> Err com a mensagem. */
  @Test
  void erroQuandoGatewayLancaAiImageException() throws Exception {
    when(model.call(any())).thenThrow(new AiImageException("HttpClientException [400]: bad model"));

    GenerateResult result = service.generate(bytesDaPedraDeTeste());

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).contains("400").contains("bad model");
  }

  /**
   * Caso #8 (contrato do prompt): o {@link ImageEditPrompt} passado ao gateway deve carregar duas
   * imagens na ordem ambiente -> pedra, e o prompt fixo de {@link EditPrompts#COUNTERTOP}.
   * Substitui o antigo matcher multipart, que agora vive no teste do gateway.
   */
  @Test
  void promptEnviadoTemDuasImagensNaOrdemAmbientePedraComPromptFixo() throws Exception {
    when(model.call(any())).thenReturn(respostaComB64("aA==", false));

    service.generate(bytesDaPedraDeTeste());

    ArgumentCaptor<ImageEditPrompt> captor = ArgumentCaptor.forClass(ImageEditPrompt.class);
    org.mockito.Mockito.verify(model).call(captor.capture());
    ImageEditPrompt prompt = captor.getValue();
    assertThat(prompt.instructions()).isEqualTo(EditPrompts.COUNTERTOP);
    assertThat(prompt.options()).isInstanceOf(AiImageOptions.class);
    assertThat(prompt.inputImages()).hasSize(2);
    assertThat(prompt.inputImages().get(0).filename()).isEqualTo("ambiente.jpg");
    assertThat(prompt.inputImages().get(1).filename()).isEqualTo("ambiente.png");
  }

  /** Le a pedra de teste (ambiente.png do classpath) como bytes. */
  private static byte[] bytesDaPedraDeTeste() throws Exception {
    try (var in = new ClassPathResource("test-images/ambiente.png").getInputStream()) {
      return in.readAllBytes();
    }
  }

  /**
   * Constroi uma {@link ImageResponse} de teste com o b64 dado. Quando {@code comUsage}, popula o
   * usage (e o raw) para validar a propagacao ate {@link GenerateResult.Ok}.
   */
  private static ImageResponse respostaComB64(String b64, boolean comUsage) throws Exception {
    var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
    var raw =
        comUsage
            ? mapper.readTree("{\"data\":[{\"b64_json\":\"" + b64 + "\"}],\"usage\":{}}")
            : mapper.readTree("{\"data\":[{\"b64_json\":\"" + b64 + "\"}]}");
    var usage = comUsage ? raw.get("usage") : null;
    return new ImageResponse(
        List.of(ImageGeneration.of(Image.of(b64))), new ImageResponseMetadata(usage), raw);
  }
}
