package com.marmore.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Configuracao de seguranca da API. Autenticacao stateless por API key no header {@code X-API-Key}
 * (filtro {@link ApiKeyAuthFilter} antes do {@link AuthorizationFilter}). CSRF desabilitado (API
 * stateless por header, sem cookies de sessao). Form login e Basic auth default desabilitados
 * (elimina a senha gerada e o redirect para /login).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  /**
   * Cadeia de filtros de seguranca.
   *
   * @param http builder do Spring Security (Lambda DSL, Spring Security 7)
   * @param apiKeyFilter filtro de autenticacao por API key
   * @return cadeia configurada
   * @throws Exception em erro de configuracao
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, ApiKeyAuthFilter apiKeyFilter)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .addFilterBefore(apiKeyFilter, AuthorizationFilter.class)
        .httpBasic(b -> b.disable())
        .formLogin(f -> f.disable());
    return http.build();
  }
}
