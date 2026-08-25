/*
 * SPDX-License-Identifier: MIT
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
