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

import java.time.Instant;

/**
 * The JSON body returned for any failed request.
 *
 * <p>Mirrors the field names Spring Boot's default error attributes use, so a client written
 * against the Whitelabel JSON payload keeps working.
 *
 * @param timestamp when the failure was rendered
 * @param status the HTTP status code, for example {@code 404}
 * @param error the HTTP reason phrase, for example {@code Not Found}
 * @param message detail about the failure, or a placeholder when the container supplied none
 * @param path the request URI that failed
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
public record ErrorResponse(
    Instant timestamp, int status, String error, String message, String path) {}
