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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;

/**
 * Unit tests for {@link AppInitEventListener}.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
@ExtendWith(MockitoExtension.class)
class AppInitEventListenerTest {

  @Mock private ServletWebServerInitializedEvent event;

  @Mock private WebServer webServer;

  private AppInitEventListener listener;

  @BeforeEach
  void setUp() {
    listener = new AppInitEventListener();
  }

  @Test
  @DisplayName("the listener reads the port off the initialized web server")
  void onApplicationEventReadsThePort() {
    when(event.getWebServer()).thenReturn(webServer);
    when(webServer.getPort()).thenReturn(8080);

    assertThatCode(() -> listener.onApplicationEvent(event)).doesNotThrowAnyException();

    verify(webServer).getPort();
  }

  @Test
  @DisplayName("startup still succeeds when the local host name cannot be resolved")
  void onApplicationEventToleratesAnUnknownHost() {
    when(event.getWebServer()).thenReturn(webServer);
    when(webServer.getPort()).thenReturn(0);

    try (MockedStatic<InetAddress> inetAddress = Mockito.mockStatic(InetAddress.class)) {
      inetAddress
          .when(InetAddress::getLocalHost)
          .thenThrow(new UnknownHostException("no such host"));

      assertThatCode(() -> listener.onApplicationEvent(event)).doesNotThrowAnyException();
    }

    verify(webServer).getPort();
  }
}
