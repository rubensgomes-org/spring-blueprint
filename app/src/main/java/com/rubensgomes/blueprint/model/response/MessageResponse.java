/*
 * SPDX-License-Identifier: MIT
 */
package com.rubensgomes.blueprint.model.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * A very basic message response type.
 *
 * <p>Declared as a record: the accessor, {@code equals}, {@code hashCode} and {@code toString} are
 * all generated, which is exactly the value-object contract this type needs. Records are implicitly
 * final and their components implicitly final, so immutability is structural rather than
 * conventional.
 *
 * @param message any text to be in the response
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
public record MessageResponse(
    @Valid @NotBlank(message = "message cannot be blank") String message) {}
