/*
 * SPDX-License-Identifier: MIT
 */
package com.rubensgomes.blueprint.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubensgomes.blueprint.model.response.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link GlobalErrorController}.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
class GlobalErrorControllerTest {

  private static final String FAILED_PATH = "/api/v1/does-not-exist";
  private static final String DETAIL = "No static resource.";

  private final GlobalErrorController controller = new GlobalErrorController();

  private static MockHttpServletRequest requestWith(Object status, Object uri, Object message) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (status != null) {
      request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
    }
    if (uri != null) {
      request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, uri);
    }
    if (message != null) {
      request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
    }
    return request;
  }

  @Test
  @DisplayName("a 404 is reported with its status, reason phrase, detail and path")
  void reportsTheFailedRequest() {
    ResponseEntity<ErrorResponse> response =
        controller.handleError(requestWith(404, FAILED_PATH, DETAIL));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody())
        .isNotNull()
        .satisfies(
            body -> {
              assertThat(body.status()).isEqualTo(404);
              assertThat(body.error()).isEqualTo("Not Found");
              assertThat(body.message()).isEqualTo(DETAIL);
              assertThat(body.path()).isEqualTo(FAILED_PATH);
              assertThat(body.timestamp()).isNotNull();
            });
  }

  @Test
  @DisplayName("the response status matches the body status")
  void responseStatusMatchesTheBody() {
    ResponseEntity<ErrorResponse> response =
        controller.handleError(requestWith(400, FAILED_PATH, DETAIL));

    assertThat(response.getStatusCode().value()).isEqualTo(response.getBody().status());
  }

  @Nested
  @DisplayName("status resolution falls back to 500")
  class StatusFallback {

    @Test
    @DisplayName("when the container recorded no status")
    void whenTheAttributeIsAbsent() {
      ResponseEntity<ErrorResponse> response =
          controller.handleError(requestWith(null, FAILED_PATH, DETAIL));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
    }

    @Test
    @DisplayName("when the status attribute is not an Integer")
    void whenTheAttributeIsNotAnInteger() {
      ResponseEntity<ErrorResponse> response =
          controller.handleError(requestWith("404", FAILED_PATH, DETAIL));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("when the status code is one Spring does not recognise")
    void whenTheStatusCodeIsUnknown() {
      ResponseEntity<ErrorResponse> response =
          controller.handleError(requestWith(799, FAILED_PATH, DETAIL));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Nested
  @DisplayName("missing attributes are replaced with placeholders")
  class AttributeFallback {

    @Test
    @DisplayName("an absent request URI becomes the unknown-path placeholder")
    void absentPath() {
      ResponseEntity<ErrorResponse> response =
          controller.handleError(requestWith(404, null, DETAIL));

      assertThat(response.getBody().path()).isEqualTo(GlobalErrorController.UNKNOWN_PATH);
    }

    @Test
    @DisplayName("an absent detail message becomes the no-detail placeholder")
    void absentMessage() {
      ResponseEntity<ErrorResponse> response =
          controller.handleError(requestWith(404, FAILED_PATH, null));

      assertThat(response.getBody().message()).isEqualTo(GlobalErrorController.NO_DETAIL_MESSAGE);
    }

    @Test
    @DisplayName("a blank detail message becomes the no-detail placeholder")
    void blankMessage() {
      ResponseEntity<ErrorResponse> response =
          controller.handleError(requestWith(404, FAILED_PATH, "   "));

      assertThat(response.getBody().message()).isEqualTo(GlobalErrorController.NO_DETAIL_MESSAGE);
    }
  }
}
