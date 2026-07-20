package com.marmore.api;

import com.marmore.api.image.config.ImageEditProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Ponto de entrada da aplicação marmore-api. */
@EnableConfigurationProperties(ImageEditProperties.class)
@SpringBootApplication
public class ApiApplication {

  /**
   * Inicializa a aplicação Spring Boot.
   *
   * @param args argumentos de linha de comando
   */
  public static void main(String[] args) {
    SpringApplication.run(ApiApplication.class, args);
  }
}
