package com.marmore.api.image.web;

import com.marmore.api.image.domain.EditOptions;
import com.marmore.api.image.domain.GenerateResult;
import com.marmore.api.image.service.ImageEditService;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoint HTTP para edicao de imagem. Recebe prompt + imagens em multipart, devolve o PNG
 * resultante da chamada ao endpoint {@code /v1/images/edits} da OpenAI.
 */
@RestController
@RequestMapping("/images")
public class ImageEditController {

  private final ImageEditService service;

  /**
   * Construtor.
   *
   * @param service servico de edicao
   */
  public ImageEditController(ImageEditService service) {
    this.service = service;
  }

  /**
   * POST /images/edit.
   *
   * @param prompt prompt de edicao
   * @param images imagens de entrada (1+)
   * @return PNG gerado (200) ou erro (4xx/5xx)
   * @throws IOException se falhar a leitura de algum MultipartFile
   */
  @PostMapping(value = "/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> edit(
      @RequestParam("prompt") String prompt, @RequestParam("images") List<MultipartFile> images)
      throws IOException {
    List<ByteArrayResource> recursos = new java.util.ArrayList<>();
    for (MultipartFile mf : images) {
      String nome = mf.getOriginalFilename() != null ? mf.getOriginalFilename() : "image";
      recursos.add(new NamedByteArrayResource(mf.getBytes(), nome));
    }

    GenerateResult resultado =
        service.generate(prompt, List.copyOf(recursos), EditOptions.defaults());
    if (resultado instanceof GenerateResult.Err err) {
      throw new ImageEditException(statusPara(err.error()), err.error());
    }
    GenerateResult.Ok ok = (GenerateResult.Ok) resultado;
    byte[] png = Base64.getDecoder().decode(ok.b64());
    return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
  }

  /**
   * Mapeia a mensagem de erro para status HTTP apropriado.
   *
   * @param mensagem mensagem vinda do {@link GenerateResult.Err}
   * @return status HTTP correspondente
   */
  private static org.springframework.http.HttpStatus statusPara(String mensagem) {
    if (mensagem.startsWith("OPENAI_API_KEY")) {
      return org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
    }
    if (mensagem.startsWith("imagem de entrada ausente")) {
      return org.springframework.http.HttpStatus.BAD_REQUEST;
    }
    return org.springframework.http.HttpStatus.BAD_GATEWAY;
  }

  /** ByteArrayResource com nome de arquivo definido (exige getFilename nao nulo para multipart). */
  private static final class NamedByteArrayResource extends ByteArrayResource {
    private final String filename;

    NamedByteArrayResource(byte[] bytes, String filename) {
      super(bytes);
      this.filename = filename;
    }

    @Override
    public String getFilename() {
      return filename;
    }
  }
}
