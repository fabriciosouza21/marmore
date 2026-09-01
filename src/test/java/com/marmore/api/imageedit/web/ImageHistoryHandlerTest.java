package com.marmore.api.imageedit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marmore.api.imageedit.domain.CatalogoPedras;
import com.marmore.api.imageedit.domain.Pedra;
import com.marmore.api.imageedit.storage.GeneratedImage;
import com.marmore.api.imageedit.storage.GeneratedImageRepository;
import com.marmore.api.imageedit.storage.ImageObjectStorage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Testes do {@link ImageHistoryHandler} no GET /images. Contrato do JSON de saida: array com
 * resumos snake_case em PT (criado_em, custo_brl, latencia_ms, pedra, nome_pedra, produto,
 * nome_produto), criado_em como ISO-8601, custo nulo preservado, produto/nome_produto nulos quando
 * a imagem nao tem produto gravado, nome_pedra resolvido do {@link CatalogoPedras} (nulo quando a
 * imagem nao tem pedra gravada ou o id nao existe no catalogo) e ordem exatamente como devolvida
 * pelo repositorio. A serializacao Jackson e real: WebTestClient ligado direto ao {@link
 * ImageHistoryRouter}, sem contexto Spring. O repositorio e o catalogo sao mocks ({@link
 * ImageObjectStorage} so satisfaz o construtor do handler).
 */
class ImageHistoryHandlerTest {

  private GeneratedImageRepository repository;
  private ImageObjectStorage storage;
  private CatalogoPedras catalogo;
  private WebTestClient client;

  @BeforeEach
  void setUp() {
    repository = mock(GeneratedImageRepository.class);
    storage = mock(ImageObjectStorage.class);
    catalogo = mock(CatalogoPedras.class);
    ImageHistoryHandler handler = new ImageHistoryHandler(repository, storage, catalogo);
    client =
        WebTestClient.bindToRouterFunction(new ImageHistoryRouter().imageHistoryRoute(handler))
            .build();
  }

  @DisplayName(
      "GET /images: 2 resumos em snake_case, ordem do repositório, custo nulo preservado, pedra,"
          + " nome_pedra e produto/nome_produto")
  @Test
  void listaResumosEmSnakeCaseNaOrdemDoRepositorio() {
    GeneratedImage nova =
        imagem(
            UUID.randomUUID(),
            Instant.parse("2026-08-27T12:00:00Z"),
            "gpt-image-2",
            new BigDecimal("0.03"),
            1234L,
            "pedra-basalto",
            "pia-americana");
    GeneratedImage antiga =
        imagem(
            UUID.randomUUID(),
            Instant.parse("2026-08-26T09:30:00Z"),
            "gpt-image-1",
            null,
            800L,
            "pedra-marmore",
            null);
    when(repository.findAllByOrderByCriadoEmDesc()).thenReturn(List.of(nova, antiga));
    when(catalogo.porId("pedra-basalto"))
        .thenReturn(
            Optional.of(new Pedra("pedra-basalto", "Basalto Negro", "Granitos", "basalto.jpg")));
    when(catalogo.porId("pedra-marmore"))
        .thenReturn(
            Optional.of(new Pedra("pedra-marmore", "Marmore Branco", "Marmores", "marmore.jpg")));

    client
        .get()
        .uri("/images")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.length()")
        .isEqualTo(2)
        .jsonPath("[0].id")
        .isEqualTo(nova.getId().toString())
        .jsonPath("[0].criado_em")
        .isEqualTo("2026-08-27T12:00:00Z")
        .jsonPath("[0].modelo")
        .isEqualTo("gpt-image-2")
        .jsonPath("[0].custo_brl")
        .isEqualTo(0.03)
        .jsonPath("[0].latencia_ms")
        .isEqualTo(1234)
        .jsonPath("[0].pedra")
        .isEqualTo("pedra-basalto")
        .jsonPath("[0].nome_pedra")
        .isEqualTo("Basalto Negro")
        .jsonPath("[0].produto")
        .isEqualTo("pia-americana")
        .jsonPath("[0].nome_produto")
        .isEqualTo("Pia americana")
        .jsonPath("[1].id")
        .isEqualTo(antiga.getId().toString())
        .jsonPath("[1].criado_em")
        .isEqualTo("2026-08-26T09:30:00Z")
        .jsonPath("[1].modelo")
        .isEqualTo("gpt-image-1")
        .jsonPath("[1].custo_brl")
        .value(custo -> assertThat(custo).isNull())
        .jsonPath("[1].latencia_ms")
        .isEqualTo(800)
        .jsonPath("[1].pedra")
        .isEqualTo("pedra-marmore")
        .jsonPath("[1].nome_pedra")
        .isEqualTo("Marmore Branco")
        .jsonPath("[1].produto")
        .value(produto -> assertThat(produto).isNull())
        .jsonPath("[1].nome_produto")
        .value(nomeProduto -> assertThat(nomeProduto).isNull());
  }

  @DisplayName(
      "GET /images: nome_pedra nulo quando a pedra não está gravada ou não está no catálogo")
  @Test
  void retornaNomePedraNuloQuandoPedraNaoGravadaOuAusenteNoCatalogo() {
    GeneratedImage semPedra =
        imagem(
            UUID.randomUUID(),
            Instant.parse("2026-08-27T12:00:00Z"),
            "gpt-image-1",
            null,
            100L,
            null,
            null);
    GeneratedImage pedraSumida =
        imagem(
            UUID.randomUUID(),
            Instant.parse("2026-08-26T09:30:00Z"),
            "gpt-image-1",
            null,
            100L,
            "pedra-sumida",
            null);
    when(repository.findAllByOrderByCriadoEmDesc()).thenReturn(List.of(semPedra, pedraSumida));
    when(catalogo.porId("pedra-sumida")).thenReturn(Optional.empty());

    client
        .get()
        .uri("/images")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("[0].nome_pedra")
        .value(nomePedra -> assertThat(nomePedra).isNull())
        .jsonPath("[1].nome_pedra")
        .value(nomePedra -> assertThat(nomePedra).isNull());

    verify(catalogo, never()).porId(null);
  }

  @DisplayName("GET /images/{id}/arquivo: 200 com image/png e os bytes baixados do storage")
  @Test
  void baixaArquivoPngDaImagemExistente() {
    UUID id = UUID.randomUUID();
    GeneratedImage entidade =
        imagem(id, Instant.parse("2026-08-27T12:00:00Z"), "gpt-image-2", null, 100L, null, null);
    when(repository.findById(id)).thenReturn(Optional.of(entidade));
    when(storage.baixar(any())).thenReturn(Mono.just(new byte[] {1, 2, 3}));

    client
        .get()
        .uri("/images/" + id + "/arquivo")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.IMAGE_PNG)
        .expectBody(byte[].class)
        .isEqualTo(new byte[] {1, 2, 3});
  }

  @DisplayName("GET /images/{id}/arquivo: 404 quando o id nao existe no repositorio")
  @Test
  void retorna404QuandoIdNaoExiste() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    client.get().uri("/images/" + id + "/arquivo").exchange().expectStatus().isNotFound();
  }

  @DisplayName("GET /images/{id}/arquivo: 404 quando o id e malformado")
  @Test
  void retorna404QuandoIdMalformado() {
    client.get().uri("/images/nao-uuid/arquivo").exchange().expectStatus().isNotFound();
  }

  private static GeneratedImage imagem(
      UUID id,
      Instant criadoEm,
      String modelo,
      BigDecimal custoBrl,
      long latenciaMs,
      String pedra,
      String produto) {
    GeneratedImage entidade = new GeneratedImage();
    entidade.setId(id);
    entidade.setCriadoEm(criadoEm);
    entidade.setLatenciaMs(latenciaMs);
    entidade.setCustoBrl(custoBrl);
    entidade.setModelo(modelo);
    entidade.setObjetoKey("imagens/" + id + ".png");
    entidade.setPedra(pedra);
    entidade.setProduto(produto);
    return entidade;
  }
}
