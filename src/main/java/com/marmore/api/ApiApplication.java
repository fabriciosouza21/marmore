package com.marmore.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Ponto de entrada da aplicação marmore-api. */
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
