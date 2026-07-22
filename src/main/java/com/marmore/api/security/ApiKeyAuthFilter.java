package com.marmore.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro de autenticacao por API key no header {@value #HEADER}. Compara a chave fornecida com a
 * configurada em {@link ApiKeyProperties} via {@link MessageDigest#isEqual} (comparacao constante
 * no tempo, evita timing attack). Chave valida autentica o request e continua a chain; ausente ou
 * invalida responde 401 JSON e interrompe.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

  /** Nome do header HTTP que carrega a API key. */
  public static final String HEADER = "X-API-Key";

  private final ApiKeyProperties props;

  /**
   * Construtor.
   *
   * @param props propriedades com a chave esperada
   */
  public ApiKeyAuthFilter(ApiKeyProperties props) {
    this.props = props;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String provided = request.getHeader(HEADER);
    String expected = props.getKey();
    if (provided != null
        && expected != null
        && MessageDigest.isEqual(
            provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
      SecurityContextHolder.getContext()
          .setAuthentication(
              UsernamePasswordAuthenticationToken.authenticated("apikey", null, List.of()));
      filterChain.doFilter(request, response);
    } else {
      rejeitar(response);
    }
  }

  private static void rejeitar(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response.getWriter().write("{\"error\":\"API key ausente ou invalida\"}");
  }
}
