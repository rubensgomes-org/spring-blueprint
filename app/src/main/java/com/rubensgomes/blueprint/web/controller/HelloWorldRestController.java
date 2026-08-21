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
package com.rubensgomes.blueprint.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rubensgomes.blueprint.model.response.MessageResponse;
import com.rubensgomes.blueprint.service.HelloWorldService;

import lombok.extern.slf4j.Slf4j;

/**
 * A very simple {@link RestController} that responds with a "Hello World!" message.
 *
 * <p>This class uses the pattern of delegating business responsibility calls to the {@link
 * HelloWorldService} service layer.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
@Slf4j
@RestController
public class HelloWorldRestController {

  /** Constant becomes handy in unit testing. */
  public static final String HELLO_WORLD_OPERATION_PATH = "/api/v1/helloworld";

  private final HelloWorldService service;

  /**
   * Creates a controller backed by the given service layer.
   *
   * @param service the service the request handling is delegated to
   */
  @Autowired
  public HelloWorldRestController(HelloWorldService service) {
    this.service = service;
  }

  /**
   * A very basic Hello World! operation.
   *
   * @return the "Hello World!" greeting message response
   */
  @GetMapping(path = HELLO_WORLD_OPERATION_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<MessageResponse> helloWorld() {
    log.trace("helloWorld()");
    MessageResponse response = service.helloWorld();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }
}
