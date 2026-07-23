package com.marmore.api.imageedit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marmore.api.imageedit.ai.AiImageException;
import com.marmore.api.imageedit.ai.Image;
import com.marmore.api.imageedit.ai.ImageEditModel;
import com.marmore.api.imageedit.ai.ImageEditPrompt;
import com.marmore.api.imageedit.ai.ImageGeneration;
import com.marmore.api.imageedit.ai.ImageResponse;
import com.marmore.api.imageedit.ai.ImageResponseMetadata;
import com.marmore.api.imageedit.config.ImageEditProperties;
import com.marmore.api.imageedit.cost.ImageCostCalculator;
import com.marmore.api.imageedit.cost.UsdBrlProvider;
import com.marmore.api.imageedit.domain.EditPrompts;
import com.marmore.api.imageedit.domain.GenerateResult;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testes unitarios do {@link ImageEditService} reativo. Foco no papel do service: orquestrar
 * validacoes pre-rede, resize, chamada ao gateway reativo e calculo de custo, traduzindo qualquer
 * falha em {@link GenerateResult.Err} (nenhum caminho lanca). Os cinco colaboradores sao mocks.
 */
class ImageEditServiceTest {

  private ImageEditProperties props;
  private ImageResizer resizer;
  private ImageEditModel model;
  private ImageCostCalculator calculator;
  private UsdBrlProvider usdBrl;
  private ImageEditService service;

  /**
   * Caminho da pedra de teste (ambiente.png do classpath), resolvido em runtime. Reaproveitado do
   * teste legado para nao depender de um arquivo de pedra real no classpath.
   */
  private static Path pedraValida() {
    try {
      return new org.springframework.core.io.ClassPathResource("test-images/ambiente.png")
          .getFile()
          .toPath();
    } catch (java.io.IOException e) {
      throw new IllegalStateException("ambiente.png ausente do classpath", e);
    }
  }

  private static byte[] bytesDaPedraDeTeste() throws Exception {
    try (var in =
        new org.springframework.core.io.ClassPathResource("test-images/ambiente.png")
            .getInputStream()) {
      return in.readAllBytes();
    }
  }

  @BeforeEach
  void setUp() {
    props = new ImageEditProperties();
    props.setApiKey("chave-teste");
    props.setStonePath(pedraValida());
    resizer = new ImageResizer();
    model = org.mockito.Mockito.mock(ImageEditModel.class);
    calculator = new ImageCostCalculator();
    usdBrl = org.mockito.Mockito.mock(UsdBrlProvider.class);
    when(usdBrl.currentRate()).thenReturn(monoJust(new BigDecimal("5.00")));
    service = new ImageEditService(props, resizer, model, calculator, usdBrl);
  }

  @DisplayName("sucesso: gateway devolve b64 -> Ok com b64 e custo BRL calculado")
  @Test
  void sucessoQuandoGatewayDevolveB64RetornaOkComCusto() throws Exception {
    when(model.call(any())).thenReturn(monoJust(respostaComB64("aGVsbG8=")));

    GenerateResult result = service.generate(bytesDaPedraDeTeste()).block();

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    GenerateResult.Ok ok = (GenerateResult.Ok) result;
    assertThat(ok.b64()).isEqualTo("aGVsbG8=");
    assertThat(ok.raw()).isNotNull();
    assertThat(ok.usage()).isNotNull();
    assertThat(ok.cost()).as("custo computado").isNotNull();
    assertThat(ok.cost().costUsd()).isEqualByComparingTo(new BigDecimal("0.053"));
    assertThat(ok.cost().costBrl()).isEqualByComparingTo(new BigDecimal("0.26500"));
    assertThat(ok.latencyMs()).isGreaterThanOrEqualTo(0L);
  }

  @DisplayName("sucesso: custo ausente na tabela -> Ok com custo nulo")
  @Test
  void sucessoComModeloSemPrecoRetornaOkComCustoNulo() throws Exception {
    // Forca o calculator a nao achar a combinacao (ImageCostCalculator e final, logo mock).
    ImageCostCalculator semPreco = org.mockito.Mockito.mock(ImageCostCalculator.class);
    when(semPreco.costUsd(anyString(), anyString(), anyString()))
        .thenReturn(java.util.Optional.empty());
    service = new ImageEditService(props, resizer, model, semPreco, usdBrl);
    when(model.call(any())).thenReturn(monoJust(respostaComB64("aA==")));

    GenerateResult result = service.generate(bytesDaPedraDeTeste()).block();

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    assertThat(((GenerateResult.Ok) result).b64()).isEqualTo("aA==");
    assertThat(((GenerateResult.Ok) result).cost()).isNull();
  }

  @DisplayName("apiKey ausente -> Err sem chamar o gateway")
  @Test
  void erroQuandoApiKeyVaziaSemChamarGateway() throws Exception {
    props.setApiKey("");

    GenerateResult result = service.generate(bytesDaPedraDeTeste()).block();

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("OPENAI_API_KEY");
    verify(model, never()).call(any());
  }

  @DisplayName("pedra (stone-path) inexistente -> Err sem chamar o gateway")
  @Test
  void erroQuandoPedraAusenteSemChamarGateway() throws Exception {
    props.setStonePath(Paths.get("/tmp/arquivo-que-nao-existe.png"));

    GenerateResult result = service.generate(bytesDaPedraDeTeste()).block();

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("stone image not found");
    verify(model, never()).call(any());
  }

  @DisplayName("ambiente indecodificavel -> Err sem chamar o gateway")
  @Test
  void erroQuandoImagemIndecodificavelSemChamarGateway() {
    GenerateResult result = service.generate(new byte[] {1, 2, 3, 4}).block();

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("unable to decode input image");
    verify(model, never()).call(any());
  }

  @DisplayName("gateway lanca AiImageException -> Err com a mensagem da excecao")
  @Test
  void erroQuandoGatewayLancaAiImageException() throws Exception {
    when(model.call(any())).thenReturn(monoError(new AiImageException("[400]: bad model")));

    GenerateResult result = service.generate(bytesDaPedraDeTeste()).block();

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).contains("400").contains("bad model");
  }

  @DisplayName("resposta sem b64_json -> Err")
  @Test
  void erroQuandoRespostaSemB64() throws Exception {
    when(model.call(any())).thenReturn(monoJust(respostaSemB64()));

    GenerateResult result = service.generate(bytesDaPedraDeTeste()).block();

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).startsWith("resposta sem b64_json");
  }

  @DisplayName("prompt enviado ao gateway tem duas imagens (ambiente, pedra) e prompt fixo")
  @Test
  void promptEnviadoTemDuasImagensNaOrdemAmbientePedraComPromptFixo() throws Exception {
    when(model.call(any())).thenReturn(monoJust(respostaComB64("aA==")));

    service.generate(bytesDaPedraDeTeste()).block();

    ArgumentCaptor<ImageEditPrompt> captor = ArgumentCaptor.forClass(ImageEditPrompt.class);
    verify(model).call(captor.capture());
    ImageEditPrompt prompt = captor.getValue();
    assertThat(prompt.instructions()).isEqualTo(EditPrompts.COUNTERTOP);
    assertThat(prompt.inputImages()).hasSize(2);
    assertThat(prompt.inputImages().get(0).filename()).isEqualTo("ambiente.jpg");
    assertThat(prompt.inputImages().get(1).filename())
        .isEqualTo(props.getStonePath().getFileName().toString());
  }

  @DisplayName("erro de cotacao USD->BRL nao quebra o fluxo: ainda retorna Ok")
  @Test
  void erroDeCotacaoNaoQuebraGeracao() throws Exception {
    when(usdBrl.currentRate()).thenReturn(monoError(new IllegalStateException("cotacao fora")));
    when(model.call(any())).thenReturn(monoJust(respostaComB64("aA==")));

    GenerateResult result = service.generate(bytesDaPedraDeTeste()).block();

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    assertThat(((GenerateResult.Ok) result).b64()).isEqualTo("aA==");
  }

  private static reactor.core.publisher.Mono<BigDecimal> monoJust(BigDecimal v) {
    return reactor.core.publisher.Mono.just(v);
  }

  private static <T> reactor.core.publisher.Mono<T> monoJust(T v) {
    return reactor.core.publisher.Mono.just(v);
  }

  private static <T> reactor.core.publisher.Mono<T> monoError(Throwable e) {
    return reactor.core.publisher.Mono.error(e);
  }

  private static ImageResponse respostaComB64(String b64) throws Exception {
    var mapper = JsonMapper.builder().build();
    var raw = mapper.readTree("{\"data\":[{\"b64_json\":\"" + b64 + "\"}],\"usage\":{}}");
    return new ImageResponse(
        List.of(ImageGeneration.of(Image.of(b64))),
        new ImageResponseMetadata(raw.get("usage")),
        raw);
  }

  private static ImageResponse respostaSemB64() throws Exception {
    var mapper = JsonMapper.builder().build();
    var raw = mapper.readTree("{\"data\":[{}]}");
    return new ImageResponse(
        List.of(ImageGeneration.of(Image.of(null))), new ImageResponseMetadata(null), raw);
  }
}
