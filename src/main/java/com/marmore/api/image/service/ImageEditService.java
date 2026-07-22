package com.marmore.api.image.service;

import com.marmore.api.image.ai.ImageEditModel;
import com.marmore.api.imageedit.ai.AiImageException;
import com.marmore.api.imageedit.ai.AiImageOptions;
import com.marmore.api.imageedit.ai.ImageEditPrompt;
import com.marmore.api.imageedit.ai.ImageResponse;
import com.marmore.api.imageedit.ai.InputImage;
import com.marmore.api.imageedit.config.ImageEditProperties;
import com.marmore.api.imageedit.domain.EditPrompts;
import com.marmore.api.imageedit.domain.GenerateResult;
import com.marmore.api.imageedit.service.ImageResizer;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Servico de edicao de imagem. Orquestra as validacoes pre-rede (api-key, pedra em disco, resize do
 * ambiente), monta o {@link ImageEditPrompt} e delega a chamada ao {@link ImageEditModel}
 * (gateway). Atua como tradutor entre o contrato do gateway (que lanca {@link AiImageException} em
 * falha, como o Spring AI) e o contrato interno {@link GenerateResult} (Ok/Err, nunca lanca).
 * Nenhum caminho lanca excecao: falhas viram {@link GenerateResult.Err}.
 */
@Service
public class ImageEditService {

  private final ImageEditProperties props;
  private final ImageResizer resizer;
  private final ImageEditModel model;

  /**
   * Construtor.
   *
   * @param props propriedades do modulo
   * @param resizer redimensionador de imagem em memoria
   * @param model gateway de edicao de imagem (OpenAI ou outro)
   */
  public ImageEditService(ImageEditProperties props, ImageResizer resizer, ImageEditModel model) {
    this.props = props;
    this.resizer = resizer;
    this.model = model;
  }

  /**
   * Gera/edita imagem a partir dos bytes do ambiente, injetando prompt fixo e imagem da pedra.
   *
   * @param ambiente bytes da foto do ambiente a ser editada
   * @return sucesso ou erro, nunca lanca
   */
  public GenerateResult generate(byte[] ambiente) {
    long start = System.nanoTime();
    if (props.getApiKey() == null || props.getApiKey().isBlank()) {
      return new GenerateResult.Err("OPENAI_API_KEY ausente. Defina no ambiente.", ms(start));
    }
    Resource pedra = new FileSystemResource(props.getStonePath());
    if (!pedra.exists()) {
      return new GenerateResult.Err("stone image not found: " + props.getStonePath(), ms(start));
    }
    Optional<byte[]> ambienteReduzido = resizer.resize(ambiente);
    if (ambienteReduzido.isEmpty()) {
      return new GenerateResult.Err("unable to decode input image", ms(start));
    }
    try {
      Resource pedraFinal = pedra;
      byte[] pedraBytes = pedraFinal.getContentAsByteArray();
      ImageEditPrompt prompt =
          ImageEditPrompt.of(
              EditPrompts.COUNTERTOP,
              AiImageOptions.defaults(),
              List.of(
                  InputImage.of(ambienteReduzido.get(), "ambiente.jpg"),
                  InputImage.of(pedraBytes, nomeDoArquivoDaPedra())));
      ImageResponse resp = model.call(prompt);
      if (resp.getResult() == null) {
        return new GenerateResult.Err("resposta sem geracao", ms(start));
      }
      String b64 = resp.getResult().output().b64Json();
      if (b64 == null) {
        return new GenerateResult.Err("resposta sem b64_json", ms(start));
      }
      return new GenerateResult.Ok(b64, resp.raw(), resp.metadata().usage(), ms(start));
    } catch (AiImageException e) {
      return new GenerateResult.Err(e.getMessage(), ms(start));
    } catch (Exception e) {
      return new GenerateResult.Err(
          e.getClass().getSimpleName() + ": " + e.getMessage(), ms(start));
    }
  }

  /** Extrai o nome do arquivo da pedra do path configurado. */
  private String nomeDoArquivoDaPedra() {
    Path fileName = props.getStonePath().getFileName();
    return fileName != null ? fileName.toString() : "pedra.png";
  }

  private static long ms(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }
}
