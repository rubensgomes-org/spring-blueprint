/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Rubens Gomes
 *
 * This file may contain content generated or assisted by Artificial Intelligence
 * tools and subsequently reviewed and modified by human contributors.
 * See the LICENSE file for licensing terms and additional AI disclosures.
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
