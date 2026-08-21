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

import org.springframework.stereotype.Service;

import com.rubensgomes.blueprint.model.response.MessageResponse;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

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
