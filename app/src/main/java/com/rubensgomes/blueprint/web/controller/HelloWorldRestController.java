/*
 * SPDX-License-Identifier: MIT
 */
package com.rubensgomes.blueprint.web.controller;

import com.rubensgomes.blueprint.model.response.MessageResponse;
import com.rubensgomes.blueprint.service.HelloWorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
