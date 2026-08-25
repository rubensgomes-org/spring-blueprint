/*
 * SPDX-License-Identifier: MIT
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
