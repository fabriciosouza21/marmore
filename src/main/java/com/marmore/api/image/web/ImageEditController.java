package com.marmore.api.image.web;

import com.marmore.api.image.service.ImageEditService;
import com.marmore.api.imageedit.domain.GenerateResult;
import java.io.IOException;
import java.util.Base64;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoint HTTP para edicao de imagem. Recebe apenas a foto do ambiente; o prompt fixo e a imagem
 * da pedra sao injetados pelo service. Devolve o PNG resultante do {@code /v1/images/edits} da
 * OpenAI.
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
   * @param image foto do ambiente a ser editada
   * @return PNG gerado (200) ou erro (4xx/5xx)
   * @throws IOException se falhar a leitura do MultipartFile
   */
  @PostMapping(value = "/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> edit(@RequestParam("image") MultipartFile image)
      throws IOException {
    GenerateResult resultado = service.generate(image.getBytes());
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
    if (mensagem.startsWith("stone image not found")) {
      return org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
    }
    if (mensagem.startsWith("unable to decode input image")) {
      return org.springframework.http.HttpStatus.BAD_REQUEST;
    }
    return org.springframework.http.HttpStatus.BAD_GATEWAY;
  }
}
