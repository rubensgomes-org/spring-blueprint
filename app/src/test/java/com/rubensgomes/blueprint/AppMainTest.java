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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

/**
 * Unit tests for the {@link App} entry point.
 *
 * <p>{@link SpringApplication#run(Class, String...)} is mocked out so that the entry point can be
 * exercised without actually booting a container; the context wiring itself is covered by {@link
 * AppTest}.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
class AppMainTest {

  @Test
  @DisplayName("main boots the Spring application with the App class and the given arguments")
  void mainDelegatesToSpringApplication() {
    String[] args = {"--server.port=0"};

    try (MockedStatic<SpringApplication> springApplication =
        Mockito.mockStatic(SpringApplication.class)) {
      App.main(args);

      springApplication.verify(() -> SpringApplication.run(App.class, args));
      springApplication.verifyNoMoreInteractions();
    }
  }

  @Test
  @DisplayName("main tolerates an empty argument array")
  void mainAcceptsNoArguments() {
    String[] args = {};

    try (MockedStatic<SpringApplication> springApplication =
        Mockito.mockStatic(SpringApplication.class)) {
      App.main(args);

      springApplication.verify(() -> SpringApplication.run(App.class, args));
    }
  }

  @Test
  @DisplayName("the application class is instantiable by the container")
  void appIsInstantiable() {
    assertThat(new App()).isNotNull();
  }
}
