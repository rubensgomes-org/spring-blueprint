/*
 * SPDX-License-Identifier: MIT
 */
package com.rubensgomes.blueprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A very basic Spring Boot microservice application.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
@SpringBootApplication
public class App {

  /**
   * Boots the Spring application context.
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }
}
