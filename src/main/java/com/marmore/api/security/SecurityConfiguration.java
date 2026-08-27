package com.marmore.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Configuracao de seguranca reativa da API. Autenticacao stateless por API key no header {@code
 * X-API-Key} (filtro {@link ApiKeyAuthWebFilter} na posicao {@link
 * SecurityWebFiltersOrder#AUTHENTICATION}). CSRF desabilitado (API stateless por header, sem
 * cookies de sessao). Form login e Basic auth desabilitados. Sem {@code
 * ServerSecurityContextRepository} persistente ({@link NoOpServerSecurityContextRepository}) - cada
 * request e stateless.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

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
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .addFilterAt(apiKeyFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .authorizeExchange(exchange -> exchange.anyExchange().authenticated());
    return http.build();
  }
}
