package com.marmore.api;

import com.marmore.api.imageedit.config.ImageEditProperties;
import com.marmore.api.imageedit.cost.UsdBrlProperties;
import com.marmore.api.security.ApiKeyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Ponto de entrada da aplicação marmore-api. */
@EnableConfigurationProperties({
  ImageEditProperties.class,
  ApiKeyProperties.class,
  UsdBrlProperties.class
})
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
