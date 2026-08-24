/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Rubens Gomes
 *
 * This file may contain content generated or assisted by Artificial Intelligence
 * tools and subsequently reviewed and modified by human contributors.
 * See the LICENSE file for licensing terms and additional AI disclosures.
 */
package com.rubensgomes.blueprint.service;

import com.rubensgomes.blueprint.model.response.MessageResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * A very simple service class that responds with a "Hello World!" message response.
 *
 * <p>This class uses the following patterns:
 *
 * <ul>
 *   <li>it returns a model response type {@link MessageResponse}
 *   <li>it separates front-end web layer and back-end business domain layer
 * </ul>
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
@Slf4j
@Service
public class HelloWorldService {

  /**
   * Returns the "Hello World!" greeting wrapped in a message response.
   *
   * @return the greeting message response
   */
  public MessageResponse helloWorld() {
    log.trace("helloWorld()");
    // this is where business domain layer would be called from.
    return new MessageResponse("Hello World!");
  }

  /** Logs a message when this bean is destroyed during application shutdown. */
  @PreDestroy
  public void cleanup() {
    log.info("I am being terminated.");
  }
}
