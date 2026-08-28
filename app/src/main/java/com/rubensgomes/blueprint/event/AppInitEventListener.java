/*
 * SPDX-License-Identifier: MIT
 */
package com.rubensgomes.blueprint.event;

import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Handles application initialization event to display IP and port.
 *
 * <p>Implements the observer pattern through Spring's {@link ApplicationListener} contract: the
 * container publishes lifecycle events and this bean is notified, rather than {@code App.main}
 * querying the server for its port after startup. Startup reporting therefore stays out of the
 * bootstrap path and can be removed by deleting one class.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
@Slf4j
@Component
public class AppInitEventListener implements ApplicationListener<ServletWebServerInitializedEvent> {

  /**
   * Logs the host IP address and the port the embedded servlet container is listening on.
   *
   * @param event the event published once the embedded servlet container is initialized
   */
  @Override
  public void onApplicationEvent(ServletWebServerInitializedEvent event) {
    log.info("application started");
    int port = event.getWebServer().getPort();

    try {
      InetAddress address = InetAddress.getLocalHost();
      String ip = address.getHostAddress();
      log.info("IP address {}", ip);
    } catch (UnknownHostException ex) {
      // the local host name could not be resolved; startup should still proceed.
      log.warn("failed to resolve the local host IP address: {}", ex.getMessage());
    }

    log.info("Listening port {}", port);
  }
}
