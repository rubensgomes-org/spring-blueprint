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
