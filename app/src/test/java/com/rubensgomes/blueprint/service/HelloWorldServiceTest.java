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
package com.rubensgomes.blueprint.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.rubensgomes.blueprint.model.response.MessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HelloWorldService}.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
class HelloWorldServiceTest {

  private HelloWorldService service;

  @BeforeEach
  void setUp() {
    service = new HelloWorldService();
  }

  @Test
  @DisplayName("helloWorld returns the Hello World! greeting")
  void helloWorldReturnsTheGreeting() {
    MessageResponse response = service.helloWorld();

    assertThat(response).isNotNull().isEqualTo(new MessageResponse("Hello World!"));
    assertThat(response.message()).isEqualTo("Hello World!");
  }

  @Test
  @DisplayName("helloWorld returns a fresh response on every call")
  void helloWorldReturnsANewInstanceEachCall() {
    assertThat(service.helloWorld()).isNotSameAs(service.helloWorld());
  }

  @Test
  @DisplayName("cleanup completes without raising an error")
  void cleanupDoesNotThrow() {
    assertThatCode(service::cleanup).doesNotThrowAnyException();
  }
}
