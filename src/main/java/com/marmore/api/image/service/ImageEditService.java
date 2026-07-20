package com.marmore.api.image.service;

import com.marmore.api.image.config.ImageEditProperties;
import com.marmore.api.image.domain.EditOptions;
import com.marmore.api.image.domain.GenerateResult;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Servico de edicao de imagem via endpoint {@code /v1/images/edits} da OpenAI. Nenhum caminho lanca
 * excecao: falhas viram {@link GenerateResult.Err}.
 */
@Service
public class ImageEditService {

  private final ImageEditProperties props;
  private final RestClient restClient;

  /**
   * Construtor.
   *
   * @param props propriedades do modulo
   * @param restClient cliente HTTP autenticado
   */
  public ImageEditService(ImageEditProperties props, RestClient restClient) {
    this.props = props;
    this.restClient = restClient;
  }

  /**
   * Gera/edita imagem a partir de um prompt e imagens de referencia.
   *
   * @param prompt prompt de edicao
   * @param images imagens de entrada (1+)
   * @param opts opcoes da chamada
   * @return sucesso ou erro, nunca lanca
   */
  public GenerateResult generate(String prompt, List<Resource> images, EditOptions opts) {
    long start = System.nanoTime();
    if (props.getApiKey() == null || props.getApiKey().isBlank()) {
      return new GenerateResult.Err("OPENAI_API_KEY ausente. Defina no ambiente.", ms(start));
    }
    for (Resource img : images) {
      if (!img.exists()) {
        return new GenerateResult.Err("imagem de entrada ausente: " + img.getFilename(), ms(start));
      }
    }
    throw new UnsupportedOperationException("ainda nao implementado");
  }

  private static long ms(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }
}
