package com.marmore.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/** Testes de {@link ApiKeyAuthFilter}. */
class ApiKeyAuthFilterTest {

  private boolean chainExecutada;

  @BeforeEach
  void limparContexto() {
    SecurityContextHolder.clearContext();
    chainExecutada = false;
  }

  @AfterEach
  void limpar() {
    SecurityContextHolder.clearContext();
  }

  /** Filtro configurado com a chave esperada. */
  private static ApiKeyAuthFilter filtroComChave(String esperada) {
    ApiKeyProperties props = new ApiKeyProperties();
    props.setKey(esperada);
    return new ApiKeyAuthFilter(props);
  }

  /** Chain que marca flag quando executada. */
  private FilterChain chainQueMarca() {
    return new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
        chainExecutada = true;
      }
    };
  }

  private static MockHttpServletRequest reqComHeader(String key) {
    MockHttpServletRequest req = new MockHttpServletRequest();
    if (key != null) {
      req.addHeader(ApiKeyAuthFilter.HEADER, key);
    }
    return req;
  }

  /** Chave correta autentica e continua a chain. */
  @Test
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  void chaveCorretaAutenticaEContinuaChain() throws Exception {
    ApiKeyAuthFilter filter = filtroComChave("segredo-certo");

    filter.doFilter(reqComHeader("segredo-certo"), new MockHttpServletResponse(), chainQueMarca());

    assertThat(chainExecutada).isTrue();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
  }

  /** Chave errada rejeita com 401 e NAO continua a chain. */
  @Test
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  void chaveErradaRejeitaCom401ESemContinuar() throws Exception {
    ApiKeyAuthFilter filter = filtroComChave("segredo-certo");

    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(reqComHeader("errada"), res, chainQueMarca());

    assertThat(chainExecutada).isFalse();
    assertThat(res.getStatus()).isEqualTo(401);
    assertThat(res.getContentType()).contains("application/json");
    assertThat(res.getContentAsString()).contains("API key");
  }

  /** Header ausente rejeita com 401. */
  @Test
  void headerAusenteRejeitaCom401() throws Exception {
    ApiKeyAuthFilter filter = filtroComChave("segredo-certo");

    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(reqComHeader(null), res, chainQueMarca());

    assertThat(chainExecutada).isFalse();
    assertThat(res.getStatus()).isEqualTo(401);
  }
}
