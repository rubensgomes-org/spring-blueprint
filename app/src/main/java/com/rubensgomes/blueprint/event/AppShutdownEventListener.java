/*
 * SPDX-License-Identifier: MIT
 */
package com.rubensgomes.blueprint.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

/**
 * Handles application shutdown to properly release resources.
 *
 * <p>The shutdown half of the observer pattern described on {@link AppInitEventListener}: {@link
 * ContextClosedEvent} is published on SIGTERM, so orderly-shutdown logging is a subscriber rather
 * than a shutdown hook registered by hand.
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
