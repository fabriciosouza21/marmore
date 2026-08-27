package com.marmore.api.imageedit.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.marmore.api.imageedit.TestImages;
import com.marmore.api.imageedit.ai.Image;
import com.marmore.api.imageedit.ai.ImageEditModel;
import com.marmore.api.imageedit.ai.ImageGeneration;
import com.marmore.api.imageedit.ai.ImageResponse;
import com.marmore.api.imageedit.ai.ImageResponseMetadata;
import com.marmore.api.imageedit.cost.UsdBrlProperties;
import com.marmore.api.imageedit.cost.UsdBrlProvider;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;

/**
 * Teste de integracao ponta-a-ponta do fluxo SSE de edicao de imagem. Sobe o contexto WebFlux
 * completo ({@code @SpringBootTest(webEnvironment = RANDOM_PORT)}) e dispara um {@code POST
 * /images/edit} multipart real atraves do {@link WebTestClient} contra o servidor embutido,
 * exercitando o filtro de seguranca, o router, o handler, o service e o redimensionador AWT.
 *
 * <p>O gateway OpenAI ({@link ImageEditModel}) e mockado por {@link TestMocks} via
 * {@code @Bean @Primary} para devolver um {@link ImageResponse} fixo (sem chamada de rede). O
 * {@link UsdBrlProvider} tambem e substituido por um stub que devolve cotacao fixa (sem chamar a
 * AwesomeAPI). O {@code stone-path} e apontado para um recurso de teste (PNG minimal) via {@link
 * DynamicPropertySource}, mantendo o {@code data/} do repositorio limpo.
 *
 * <p>O {@link WebTestClient} e construido manualmente apontando para a porta aleatoria do servidor
 * embutido (Spring Boot 4.1 nao registra mais um bean {@link WebTestClient} automaticamente), com
 * timeout ampliado para a leitura do stream SSE.
 *
 * <p>Assercoes: a sequencia SSE do PlantUML (status recebido -> redimensionando -> gerando -> done
 * -> imagem) chega pela rede, e a ausencia do header {@code X-API-Key} responde 401.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(ImageEditSseIntegrationTest.TestMocks.class)
class ImageEditSseIntegrationTest {

  /** API key valida configurada em {@code src/test/resources/application.yaml}. */
  private static final String API_KEY_VALIDA = "chave-teste-fixa";

  /** Base64 fixo retornado pelo gateway mockado (exibido como {@code data: <b64 cru>}). */
  private static final String B64_FIXO = "aGVsbG8="; // base64("hello")

  /** Timeout do {@link StepVerifier} e do cliente: a conclusao chega em poucos segundos. */
  private static final Duration VERIFICACAO_TIMEOUT = Duration.ofSeconds(30);

  @LocalServerPort private int porta;

  /**
   * Aponta {@code marmore.openai.image.stone-path} para um PNG minimal em {@code
   * src/test/resources/test-images/granito-test.png}, resolvido como caminho absoluta em runtime, e
   * define {@code api-key} para satisfazer a validacao do service (o gateway e mockado, mas o
   * {@link com.marmore.api.imageedit.service.ImageEditService} checa a chave antes de chamar).
   */
  @DynamicPropertySource
  static void sobrescreveStonePath(DynamicPropertyRegistry registro) {
    registro.add(
        "marmore.openai.image.stone-path",
        () -> {
          try {
            return TestImages.granitoPath().toAbsolutePath().toString();
          } catch (Exception e) {
            throw new IllegalStateException("granito-test.png ausente do classpath de teste", e);
          }
        });
    registro.add("marmore.openai.image.api-key", () -> "test-key-nao-usado-gateway-mockado");
  }

  /** Constroi o {@link WebTestClient} apontando para o servidor embutido na porta aleatoria. */
  private WebTestClient cliente() {
    return WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + porta)
        .responseTimeout(VERIFICACAO_TIMEOUT)
        .build();
  }

  @DisplayName("sucesso: multipart válido emite status×3 -> done -> imagem via SSE")
  @Test
  void sucessoEmiteSequenciaCompletaSse() throws Exception {
    byte[] ambiente = TestImages.ambiente();

    var responseBody =
        cliente()
            .post()
            .uri("/images/edit")
            .header("X-API-Key", API_KEY_VALIDA)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipartAmbiente(ambiente)))
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult(String.class)
            .getResponseBody();

    StepVerifier.create(responseBody)
        .assertNext(corpo -> assertThat(corpo).contains("\"fase\":\"recebido\""))
        .assertNext(corpo -> assertThat(corpo).contains("\"fase\":\"redimensionando\""))
        .assertNext(corpo -> assertThat(corpo).contains("\"fase\":\"gerando\""))
        .assertNext(corpo -> assertThat(corpo).contains("\"latency_ms\"").contains("\"custo_brl\""))
        .assertNext(corpo -> assertThat(corpo).isEqualTo(B64_FIXO))
        .verifyComplete();
  }

  @DisplayName("sem header X-API-Key -> 401 Unauthorized")
  @Test
  void semApiKeyRetorna401() throws Exception {
    byte[] ambiente = TestImages.ambiente();

    cliente()
        .post()
        .uri("/images/edit")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(BodyInserters.fromMultipartData(multipartAmbiente(ambiente)))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  /** Constroi o multipart com a parte "image" (foto do ambiente) a partir dos bytes dados. */
  private static MultiValueMap<String, HttpEntity<?>> multipartAmbiente(byte[] ambiente) {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("image", ambiente).filename("ambiente.png").contentType(MediaType.IMAGE_PNG);
    return builder.build();
  }

  /** Constroi o {@link ImageResponse} fixo retornado pelo gateway mockado. */
  private static ImageResponse respostaFixa() {
    try {
      var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
      JsonNode raw =
          mapper.readTree(
              "{\"data\":[{\"b64_json\":\"" + B64_FIXO + "\"}],\"usage\":{\"total_tokens\":100}}");
      return new ImageResponse(
          List.of(ImageGeneration.of(Image.of(B64_FIXO))),
          new ImageResponseMetadata(raw.get("usage")));
    } catch (Exception e) {
      throw new IllegalStateException("falha ao montar ImageResponse mockada", e);
    }
  }

  /**
   * Mocks de beans para o teste: substituem o gateway OpenAI e o provedor de cotacao. Os metodos
   * tem nomes distintos dos beans reais ({@code imageEditModel}, {@code usdBrlProvider}) para nao
   * colidir com a definicao original (Spring Boot 4 proibe override de bean de mesmo nome); o
   * {@link Primary @Primary} faz a injecao por tipo preferir o mock.
   */
  @TestConfiguration
  static class TestMocks {

    /** Gateway mockado: devolve {@link ImageEditSseIntegrationTest#respostaFixa()} sempre. */
    @Bean
    @Primary
    ImageEditModel mockImageEditModel() {
      return prompt -> Mono.just(respostaFixa());
    }

    /** Provedor de cotacao mockado: devolve 5.00 sempre, sem chamar a AwesomeAPI. */
    @Bean
    @Primary
    UsdBrlProvider mockUsdBrlProvider() {
      UsdBrlProperties props = new UsdBrlProperties(null, null, null);
      return new UsdBrlProvider(WebClient.builder(), props) {
        @Override
        public Mono<BigDecimal> currentRate() {
          return Mono.just(new BigDecimal("5.00"));
        }
      };
    }
  }
}
