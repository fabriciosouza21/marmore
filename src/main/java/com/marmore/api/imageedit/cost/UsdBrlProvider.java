package com.marmore.api.imageedit.cost;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Provedor da cotacao USD->BRL com cache em memoria por TTL e fallback em falha.
 *
 * <p>Busca o campo {@code USDBRL.bid} da AwesomeAPI via {@link WebClient}. Se a ultima busca foi ha
 * menos de {@link UsdBrlProperties#cacheTtl()}, retorna o valor em cache sem refazer a chamada. Em
 * qualquer erro (HTTP 4xx/5xx, timeout, JSON invalido) retorna {@link UsdBrlProperties#fallback()}
 * sem atualizar o cache, de modo que a proxima chamada dentro do TTL tentara a API novamente.
 *
 * <p>Le o corpo como {@code String} e desserializa com um {@link ObjectMapper} compartilhado em vez
 * de confiar em {@code bodyToMono(JsonNode.class)}: assim o provedor funciona mesmo quando o {@link
 * WebClient.Builder} injetado nao tem os codecs Jackson pre-configurados (caso dos testes com
 * MockWebServer usando um builder cru).
 *
 * <p>Timeout: 5 segundos aplicados via {@link Mono#timeout(Duration)} sobre a busca inteira. Cobrem
 * conexao, espera por cabecalhos e leitura do corpo; curto o suficiente para o fallback entrar
 * rapido em falha.
 *
 * <p>Concorrencia do cache: o campo {@code cache} e {@code volatile} e aponta para um {@link
 * CacheEntry} imutavel. A verificacao de validade e sincrona (rapida); a busca e assincrona. A
 * escrita do cache apos uma busca bem-sucedida e atomica (ultima escrita vence). Durante a janela
 * de raca na fronteira do TTL duas chamadas concorrentes podem disparar buscas duplicadas, mas
 * nenhuma corrupcao e possivel -- bloquear o event loop mantendo um lock sobre uma operacao
 * assincrona seria pior que a duplicacao rara.
 */
@Component
public class UsdBrlProvider {

  /** ObjectMapper compartilhado e thread-safe (Jackson). */
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Timeout aplicado a busca da cotacao via {@link Mono#timeout(Duration)}. */
  private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(5);

  private final WebClient client;
  private final UsdBrlProperties props;

  private volatile CacheEntry cache;

  /**
   * Constroi o provedor a partir de um {@link WebClient.Builder} (nao um {@link WebClient} pronto)
   * para que o teste aponte o cliente para um {@code MockWebServer}.
   */
  public UsdBrlProvider(WebClient.Builder builder, UsdBrlProperties props) {
    this.client = builder.build();
    this.props = props;
  }

  /**
   * Retorna a cotacao atual: valor em cache se valido, ou busca fresca na API. Em erro (incluindo
   * timeout), retorna o fallback.
   */
  public Mono<BigDecimal> currentRate() {
    CacheEntry entry = this.cache;
    if (entry != null && entry.isValid()) {
      return Mono.just(entry.value());
    }
    return client
        .get()
        .uri(props.url())
        .retrieve()
        .bodyToMono(String.class)
        .map(UsdBrlProvider::extractBid)
        .timeout(FETCH_TIMEOUT)
        .doOnNext(value -> this.cache = new CacheEntry(value, Instant.now().plus(props.cacheTtl())))
        .onErrorResume(error -> Mono.just(props.fallback()));
  }

  /** Extrai o campo {@code USDBRL.bid} do JSON da AwesomeAPI como {@link BigDecimal}. */
  private static BigDecimal extractBid(String body) {
    try {
      JsonNode root = MAPPER.readTree(body);
      JsonNode bid = root.path("USDBRL").path("bid");
      if (bid.isMissingNode() || !bid.isTextual()) {
        throw new IllegalStateException("USDBRL.bid ausente ou invalido: " + body);
      }
      return new BigDecimal(bid.asText());
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("JSON invalido da AwesomeAPI: " + body, e);
    }
  }

  /** Entrada imutavel do cache: valor + instante de expiracao. */
  private record CacheEntry(BigDecimal value, Instant expiresAt) {
    boolean isValid() {
      return Instant.now().isBefore(expiresAt);
    }
  }
}
