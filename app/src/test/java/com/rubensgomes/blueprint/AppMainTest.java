/*
 * Copyright 2026 Rubens Gomes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
