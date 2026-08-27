package com.marmore.api.imageedit.cost;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuracao do provedor de cotacao USD->BRL. Prefixo: {@code
 * marmore.cost.usd-brl}. Carrega URL da API de cambio, TTL do cache e valor de fallback; sem
 * logica.
 *
 * <p>Os defaults refletem a cotacao de 17/07/2026 (Investing) e a AwesomeAPI como fonte. A classe
 * ja esta registrada em {@code ApiApplication} via {@code @EnableConfigurationProperties}.
 *
 * @param url URL da API de cambio (default: AwesomeAPI)
 * @param cacheTtl TTL do cache em memoria (default: 6 horas)
 * @param fallback cotacao de fallback quando a API falha (default: 5.1075)
 */
@ConfigurationProperties(prefix = "marmore.cost.usd-brl")
public record UsdBrlProperties(String url, Duration cacheTtl, BigDecimal fallback) {

  /** URL default da AwesomeAPI para o par USD-BRL. */
  private static final String DEFAULT_URL = "https://economia.awesomeapi.com.br/json/last/USD-BRL";

  /** TTL default do cache: 6 horas. */
  private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(6);

  /** Cotacao default de fallback (Investing 17/07/2026). */
  private static final BigDecimal DEFAULT_FALLBACK = new BigDecimal("5.1075");

  /** Construtor compacto que aplica os tres defaults quando ausentes. */
  public UsdBrlProperties {
    if (url == null) {
      url = DEFAULT_URL;
    }
    if (cacheTtl == null) {
      cacheTtl = DEFAULT_CACHE_TTL;
    }
    if (fallback == null) {
      fallback = DEFAULT_FALLBACK;
    }
  }
}
