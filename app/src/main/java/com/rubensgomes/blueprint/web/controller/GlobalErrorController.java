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

import com.rubensgomes.blueprint.model.response.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renders every failed request as JSON instead of Spring Boot's Whitelabel HTML page.
 *
 * <p>The servlet container forwards to {@code /error} after any {@code sendError}, so this one
 * mapping covers every failure the application produces -- a 404 for an unmapped route, a 400 from
 * failed bean validation, a 500 from an unhandled exception. Implementing {@link ErrorController}
 * is what tells Spring Boot to stand down and let this handler take over.
 *
 * <p>Note that this changes only the error <em>representation</em>. A request to an unmapped path
 * still returns 404; it simply returns it as JSON, which is what a REST client expects.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
@Slf4j
@RestController
public class GlobalErrorController implements ErrorController {

  /** Constant becomes handy in unit testing. */
  public static final String ERROR_PATH = "/error";

  /** Stands in for the request URI when the container did not record one. */
  static final String UNKNOWN_PATH = "unknown";

  /** Stands in for the detail message when the container did not record one. */
  static final String NO_DETAIL_MESSAGE = "no further detail available";

  /**
   * Builds the JSON error body from the request attributes the container populated before
   * forwarding here.
   *
   * @param request the forwarded request carrying the {@code jakarta.servlet.error.*} attributes
   * @return the error body, with the same HTTP status the original request failed with
   */
  // NOTE: the accepted methods are listed explicitly rather than left to the
  // @RequestMapping default, which silently accepts every method including TRACE.
  //
  // The list is broad on purpose. The container forwards the ORIGINAL request
  // method when it dispatches to "/error", so a failed POST arrives here as a
  // POST. Narrowing this to GET would make every non-GET failure return 405
  // instead of the intended status -- a client POSTing to a mistyped URL would
  // get "405 Method Not Allowed" rather than the 404 it actually caused.
  //
  // TRACE is the one deliberate omission: the application never serves it, and
  // echoing a request back is a well-known cross-site tracing liability.
  @RequestMapping(
      path = ERROR_PATH,
      method = {
        RequestMethod.GET,
        RequestMethod.HEAD,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.PATCH,
        RequestMethod.DELETE,
        RequestMethod.OPTIONS,
      },
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    HttpStatus status = resolveStatus(request);
    String path = attribute(request, RequestDispatcher.ERROR_REQUEST_URI, UNKNOWN_PATH);
    String message = attribute(request, RequestDispatcher.ERROR_MESSAGE, NO_DETAIL_MESSAGE);
    log.warn("request to '{}' failed with status {}: {}", path, status.value(), message);

    ErrorResponse body =
        new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
    return new ResponseEntity<>(body, status);
  }

  /**
   * Reads the status the request failed with, falling back to 500 when the attribute is absent, not
   * an {@code Integer}, or not a status Spring recognises.
   *
   * @param request the forwarded request
   * @return the resolved status, never null
   */
  private static HttpStatus resolveStatus(HttpServletRequest request) {
    return Optional.ofNullable(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE))
        .filter(Integer.class::isInstance)
        .map(Integer.class::cast)
        .map(HttpStatus::resolve)
        .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * Reads a string request attribute, substituting a fallback when it is absent or blank.
   *
   * @param request the forwarded request
   * @param name the {@code jakarta.servlet.error.*} attribute name
   * @param fallback the value to use when the attribute carries nothing useful
   * @return the attribute value, or the fallback
   */
  private static String attribute(HttpServletRequest request, String name, String fallback) {
    return Optional.ofNullable(request.getAttribute(name))
        .map(Object::toString)
        .filter(value -> !value.isBlank())
        .orElse(fallback);
  }
}
