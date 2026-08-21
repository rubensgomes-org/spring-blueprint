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
package com.rubensgomes.blueprint.model.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

/**
 * A very basic message response type.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
public final class MessageResponse {

  @Valid
  @NotBlank(message = "message cannot be blank")
  private final String message;

  /**
   * Creates a response carrying the given message.
   *
   * @param message any text to be in the response
   */
  public MessageResponse(String message) {
    this.message = message;
  }

  /**
   * Returns the text carried by this response.
   *
   * @return the response message
   */
  public String getMessage() {
    return message;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;

    MessageResponse that = (MessageResponse) other;

    return Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(message);
  }

  @Override
  public String toString() {
    return "MessageResponse(" + "message='" + message + "'" + ")";
  }
}
