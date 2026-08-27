package com.marmore.api.imageedit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marmore.api.imageedit.TestImages;
import com.marmore.api.imageedit.ai.AiImageException;
import com.marmore.api.imageedit.ai.Image;
import com.marmore.api.imageedit.ai.ImageEditModel;
import com.marmore.api.imageedit.ai.ImageEditPrompt;
import com.marmore.api.imageedit.ai.ImageGeneration;
import com.marmore.api.imageedit.ai.ImageResponse;
import com.marmore.api.imageedit.ai.ImageResponseMetadata;
import com.marmore.api.imageedit.config.ImageEditProperties;
import com.marmore.api.imageedit.cost.UsdBrlProvider;
import com.marmore.api.imageedit.domain.EditPrompts;
import com.marmore.api.imageedit.domain.GenerateResult;
import com.marmore.api.imageedit.storage.GeneratedImage;
import com.marmore.api.imageedit.storage.GeneratedImageRepository;
import com.marmore.api.imageedit.storage.ImageObjectStorage;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.Base64;
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
  private UsdBrlProvider usdBrl;
  private ImageObjectStorage storage;
  private GeneratedImageRepository repository;
  private ImageEditService service;

  @BeforeEach
  void setUp() throws Exception {
    props = new ImageEditProperties();
    props.setApiKey("chave-teste");
    props.setStonePath(TestImages.ambientePath());
    resizer = new ImageResizer();
    model = org.mockito.Mockito.mock(ImageEditModel.class);
    usdBrl = org.mockito.Mockito.mock(UsdBrlProvider.class);
    when(usdBrl.currentRate()).thenReturn(monoJust(new BigDecimal("5.00")));
    storage = org.mockito.Mockito.mock(ImageObjectStorage.class);
    when(storage.salvar(any())).thenReturn(monoJust("imagens/abc.png"));
    repository = org.mockito.Mockito.mock(GeneratedImageRepository.class);
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    service = new ImageEditService(props, resizer, model, usdBrl, storage, repository);
  }

  @DisplayName("sucesso: gateway devolve b64 -> Ok com b64 e custo BRL calculado")
  @Test
  void sucessoQuandoGatewayDevolveB64RetornaOkComCusto() throws Exception {
    when(model.call(any())).thenReturn(monoJust(respostaComB64("aGVsbG8=")));

    GenerateResult result = service.generate(TestImages.ambiente()).block();

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    GenerateResult.Ok ok = (GenerateResult.Ok) result;
    assertThat(ok.b64()).isEqualTo("aGVsbG8=");
    assertThat(ok.usage()).isNotNull();
    assertThat(ok.cost()).as("custo computado").isNotNull();
    assertThat(ok.cost().costUsd()).isEqualByComparingTo(new BigDecimal("0.006"));
    assertThat(ok.cost().costBrl()).isEqualByComparingTo(new BigDecimal("0.03000"));
    assertThat(ok.latencyMs()).isGreaterThanOrEqualTo(0L);
  }

  @DisplayName("apiKey ausente -> Err generico sem vazar nome de variavel de ambiente")
  @Test
  void erroQuandoApiKeyVaziaSemChamarGateway() throws Exception {
    props.setApiKey("");

    GenerateResult result = service.generate(TestImages.ambiente()).block();

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    String msg = ((GenerateResult.Err) result).error();
    assertThat(msg).doesNotContain("OPENAI_API_KEY");
    assertThat(msg).isEqualTo("falha na geracao da imagem");
    verify(model, never()).call(any());
  }

  @DisplayName("pedra (stone-path) inexistente -> Err generico sem vazar path absoluto")
  @Test
  void erroQuandoPedraAusenteSemChamarGateway() throws Exception {
    props.setStonePath(Paths.get("/tmp/arquivo-que-nao-existe.png"));

    GenerateResult result = service.generate(TestImages.ambiente()).block();

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    String msg = ((GenerateResult.Err) result).error();
    assertThat(msg).doesNotContain("stone");
    assertThat(msg).doesNotContain("/tmp/");
    assertThat(msg).isEqualTo("falha na geracao da imagem");
    verify(model, never()).call(any());
  }

  @DisplayName("stone-path nulo -> Err (nao lanca; contrato never-throws)")
  @Test
  void erroQuandoStonePathNuloRetornaErrSemLancar() throws Exception {
    props.setStonePath(null);

    GenerateResult result = service.generate(TestImages.ambiente()).block();

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).isEqualTo("falha na geracao da imagem");
    verify(model, never()).call(any());
  }

  @DisplayName("ambiente indecodificavel -> Err de entrada invalida")
  @Test
  void erroQuandoImagemIndecodificavelSemChamarGateway() {
    GenerateResult result = service.generate(new byte[] {1, 2, 3, 4}).block();

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    String msg = ((GenerateResult.Err) result).error();
    assertThat(msg).isEqualTo("imagem de entrada invalida ou ilegivel");
    verify(model, never()).call(any());
  }

  @DisplayName("gateway lanca excecao com URL/classe interna -> Err generico sem vazar detalhe")
  @Test
  void erroQuandoGatewayLancaNaoVazaDetalheInterno() throws Exception {
    when(model.call(any()))
        .thenReturn(
            monoError(
                new AiImageException(
                    "WebClientResponseException$BadRequest: 400 from POST"
                        + " https://api.openai.com/v1/images/edits")));

    GenerateResult result = service.generate(TestImages.ambiente()).block();

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    String msg = ((GenerateResult.Err) result).error();
    assertThat(msg).doesNotContain("openai.com");
    assertThat(msg).doesNotContain("WebClientResponseException");
    assertThat(msg).doesNotContain("400");
    assertThat(msg).isEqualTo("falha na geracao da imagem");
  }

  @DisplayName("resposta sem b64_json -> Err generico")
  @Test
  void erroQuandoRespostaSemB64() throws Exception {
    when(model.call(any())).thenReturn(monoJust(respostaSemB64()));

    GenerateResult result = service.generate(TestImages.ambiente()).block();

    assertThat(result).isInstanceOf(GenerateResult.Err.class);
    assertThat(((GenerateResult.Err) result).error()).isEqualTo("falha na geracao da imagem");
  }

  @DisplayName("prompt enviado ao gateway tem duas imagens (ambiente, pedra) e prompt fixo")
  @Test
  void promptEnviadoTemDuasImagensNaOrdemAmbientePedraComPromptFixo() throws Exception {
    when(model.call(any())).thenReturn(monoJust(respostaComB64("aA==")));

    service.generate(TestImages.ambiente()).block();

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

    GenerateResult result = service.generate(TestImages.ambiente()).block();

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    assertThat(((GenerateResult.Ok) result).b64()).isEqualTo("aA==");
  }

  @DisplayName("sucesso: persiste imagem (storage + repositorio) e mantem Ok")
  @Test
  void sucessoPersisteImagemGeradaMantendoResultado() throws Exception {
    when(model.call(any())).thenReturn(monoJust(respostaComB64("aGVsbG8=")));

    GenerateResult result = service.generate(TestImages.ambiente()).block();

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    GenerateResult.Ok ok = (GenerateResult.Ok) result;
    assertThat(ok.b64()).isEqualTo("aGVsbG8=");
    assertThat(ok.cost()).isNotNull();
    assertThat(ok.cost().costBrl()).isEqualByComparingTo(new BigDecimal("0.03000"));

    ArgumentCaptor<byte[]> captorBytes = ArgumentCaptor.forClass(byte[].class);
    verify(storage).salvar(captorBytes.capture());
    assertThat(captorBytes.getValue()).isEqualTo(Base64.getDecoder().decode("aGVsbG8="));

    ArgumentCaptor<GeneratedImage> captorImagem = ArgumentCaptor.forClass(GeneratedImage.class);
    verify(repository).save(captorImagem.capture());
    GeneratedImage salva = captorImagem.getValue();
    assertThat(salva.getObjetoKey()).isEqualTo("imagens/abc.png");
    assertThat(salva.getModelo()).isEqualTo(props.getDefaultModel());
    assertThat(salva.getCustoBrl()).isEqualByComparingTo(ok.cost().costBrl());
    assertThat(salva.getLatenciaMs()).isEqualTo(ok.latencyMs());
    assertThat(salva.getCriadoEm()).isNotNull();
  }

  @DisplayName("falha no storage nao quebra o fluxo: retorna Ok e nao grava no repositorio")
  @Test
  void falhaNoStorageMantemOkSemChamarRepositorio() throws Exception {
    when(storage.salvar(any())).thenReturn(monoError(new IllegalStateException("storage fora")));
    when(model.call(any())).thenReturn(monoJust(respostaComB64("aA==")));

    GenerateResult result = service.generate(TestImages.ambiente()).block();

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    assertThat(((GenerateResult.Ok) result).b64()).isEqualTo("aA==");
    verify(repository, never()).save(any());
  }

  @DisplayName("falha no repositorio nao quebra o fluxo: retorna Ok apos bytes irem ao storage")
  @Test
  void falhaNoRepositorioMantemOkComBytesNoStorage() throws Exception {
    when(repository.save(any())).thenThrow(new IllegalStateException("banco fora"));
    when(model.call(any())).thenReturn(monoJust(respostaComB64("aA==")));

    GenerateResult result = service.generate(TestImages.ambiente()).block();

    assertThat(result).isInstanceOf(GenerateResult.Ok.class);
    assertThat(((GenerateResult.Ok) result).b64()).isEqualTo("aA==");
    verify(storage).salvar(any());
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
        List.of(ImageGeneration.of(Image.of(b64))), new ImageResponseMetadata(raw.get("usage")));
  }

  private static ImageResponse respostaSemB64() throws Exception {
    return new ImageResponse(
        List.of(ImageGeneration.of(Image.of(null))), ImageResponseMetadata.empty());
  }
}
