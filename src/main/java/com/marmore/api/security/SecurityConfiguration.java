package com.marmore.api.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Configuracao de seguranca reativa da API. Autenticacao stateless por API key no header {@code
 * X-API-Key} (filtro {@link ApiKeyAuthWebFilter} na posicao {@link
 * SecurityWebFiltersOrder#AUTHENTICATION}). CSRF desabilitado (API stateless por header, sem
 * cookies de sessao). Form login e Basic auth desabilitados. Sem {@code
 * ServerSecurityContextRepository} persistente ({@link NoOpServerSecurityContextRepository}) - cada
 * request e stateless.
 *
 * <p>CORS liberado apenas para a origem do frontend de producao ({@value #ORIGEM_FRONTEND}): o
 * navegador faz o preflight (OPTIONS) antes do POST multipart com {@code X-API-Key}, e o filtro de
 * CORS responde antes da autenticacao. O {@code GET /health} (liveness/versao) e publico, sem API
 * key.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

  /** Origem do frontend de producao (CloudFront), unica liberada no CORS. */
  static final String ORIGEM_FRONTEND = "https://marmoraria.fsmdevs.com";

  /**
   * Cadeia de filtros de seguranca reativa.
   *
   * @param http builder do Spring Security reativo (Lambda DSL)
   * @param apiKeyFilter filtro de autenticacao por API key
   * @return cadeia configurada
   */
  @Bean
  public SecurityWebFilterChain filterChain(
      ServerHttpSecurity http, ApiKeyAuthWebFilter apiKeyFilter) {
    http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .addFilterAt(apiKeyFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .authorizeExchange(
            exchange -> exchange.pathMatchers("/health").permitAll().anyExchange().authenticated());
    return http.build();
  }

  /**
   * Politica de CORS: so a origem do frontend, so GET (catalogo de pedras e imagens) e POST (a
   * edicao de imagem), headers que o cliente envia ({@code X-API-Key}, {@code Content-Type} do
   * multipart e {@code Accept} do SSE). Sem credentials (autenticacao por header, nao por cookie).
   *
   * @return fonte de configuracao usada pelo filtro de CORS do Spring Security
   */
  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration politica = new CorsConfiguration();
    politica.setAllowedOrigins(List.of(ORIGEM_FRONTEND));
    politica.setAllowedMethods(List.of("GET", "POST"));
    politica.setAllowedHeaders(List.of("X-API-Key", "Content-Type", "Accept"));
    UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
    fonte.registerCorsConfiguration("/**", politica);
    return fonte;
  }
}
