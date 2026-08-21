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
package com.rubensgomes.blueprint.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

/**
 * Handles application shutdown to properly release resources.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
@Slf4j
@Component
public class AppShutdownEventListener implements ApplicationListener<ContextClosedEvent> {

  /**
   * Logs the application context shutdown triggered by a SIGTERM.
   *
   * @param event the event published when the application context is being closed
   */
  @Override
  public void onApplicationEvent(ContextClosedEvent event) {
    log.info("Handling SIGTERM");
  }
}
