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
