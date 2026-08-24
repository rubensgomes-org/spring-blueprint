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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Unit tests for {@link AppShutdownEventListener}.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
@ExtendWith(MockitoExtension.class)
class AppShutdownEventListenerTest {

  @Mock private ContextClosedEvent event;

  @Test
  @DisplayName("the shutdown listener handles the context closed event quietly")
  void onApplicationEventHandlesTheEvent() {
    AppShutdownEventListener listener = new AppShutdownEventListener();

    assertThatCode(() -> listener.onApplicationEvent(event)).doesNotThrowAnyException();

    // the listener only logs; it must not touch the event it is handed.
    verifyNoInteractions(event);
  }
}
