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

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles application initialization event to display IP and port.
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
