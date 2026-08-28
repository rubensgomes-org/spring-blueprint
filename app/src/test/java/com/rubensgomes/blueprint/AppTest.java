/*
 * SPDX-License-Identifier: MIT
 */
package com.rubensgomes.blueprint;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Verifies that the Spring application context wires up.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
@SpringBootTest
class AppTest {

  @Autowired private ApplicationContext context;

  @Test
  void contextLoads() {
    assertNotNull(context, "application context should have been loaded");
  }
}
