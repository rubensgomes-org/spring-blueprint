/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Rubens Gomes
 *
 * This file may contain content generated or assisted by Artificial Intelligence
 * tools and subsequently reviewed and modified by human contributors.
 * See the LICENSE file for licensing terms and additional AI disclosures.
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
