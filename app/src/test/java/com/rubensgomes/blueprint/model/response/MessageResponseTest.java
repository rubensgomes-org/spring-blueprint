/*
 * SPDX-License-Identifier: MIT
 */
package com.rubensgomes.blueprint.model.response;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MessageResponse}.
 *
 * @author <a href="https://rubensgomes.com">Rubens Gomes</a>
 */
class MessageResponseTest {

  private static final String MESSAGE = "Hello World!";

  @Test
  @DisplayName("message returns the text handed to the constructor")
  void messageReturnsConstructorArgument() {
    assertThat(new MessageResponse(MESSAGE).message()).isEqualTo(MESSAGE);
  }

  @Test
  @DisplayName("a null message is carried through unchanged")
  void messageReturnsNullWhenConstructedWithNull() {
    assertThat(new MessageResponse(null).message()).isNull();
  }

  @Test
  @DisplayName("toString exposes the message")
  void toStringContainsTheMessage() {
    // The record-generated format, "Name[component=value]", replaces the
    // hand-written "Name(component='value')" this type used before it
    // became a record.
    assertThat(new MessageResponse(MESSAGE))
        .hasToString("MessageResponse[message=" + MESSAGE + "]");
  }

  @Nested
  @DisplayName("equals and hashCode")
  class EqualsAndHashCode {

    @Test
    @DisplayName("an instance equals itself")
    void equalsIsReflexive() {
      MessageResponse response = new MessageResponse(MESSAGE);
      assertThat(response.equals(response)).isTrue();
    }

    @Test
    @DisplayName("two instances carrying the same message are equal and share a hash code")
    void equalsIsTrueForTheSameMessage() {
      MessageResponse one = new MessageResponse(MESSAGE);
      MessageResponse other = new MessageResponse(MESSAGE);

      assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
      // symmetry
      assertThat(other).isEqualTo(one);
    }

    @Test
    @DisplayName("instances carrying different messages are not equal")
    void equalsIsFalseForADifferentMessage() {
      assertThat(new MessageResponse(MESSAGE)).isNotEqualTo(new MessageResponse("Goodbye!"));
    }

    @Test
    @DisplayName("a null message is only equal to another null message")
    void equalsHandlesANullMessage() {
      assertThat(new MessageResponse(null))
          .isNotEqualTo(new MessageResponse(MESSAGE))
          .isEqualTo(new MessageResponse(null));
      assertThat(new MessageResponse(MESSAGE)).isNotEqualTo(new MessageResponse(null));
    }

    @Test
    @DisplayName("an instance is never equal to null")
    void equalsIsFalseForNull() {
      assertThat(new MessageResponse(MESSAGE).equals(null)).isFalse();
    }

    @Test
    @DisplayName("an instance is never equal to an unrelated type")
    void equalsIsFalseForAnotherType() {
      assertThat(new MessageResponse(MESSAGE).equals(MESSAGE)).isFalse();
    }

    @Test
    @DisplayName("hashCode is derived from the message")
    void hashCodeIsDerivedFromTheMessage() {
      assertThat(new MessageResponse(MESSAGE)).hasSameHashCodeAs(new MessageResponse(MESSAGE));
      assertThat(new MessageResponse(null).hashCode()).isZero();
    }
  }

  @Nested
  @DisplayName("bean validation")
  class BeanValidation {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void buildValidator() {
      // A ParameterMessageInterpolator is used instead of the default one so that this
      // test does not require a Jakarta EL implementation on the test classpath.
      factory =
          Validation.byDefaultProvider()
              .configure()
              .messageInterpolator(new ParameterMessageInterpolator())
              .buildValidatorFactory();
      validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
      factory.close();
    }

    @Test
    @DisplayName("a populated message passes validation")
    void aPopulatedMessageIsValid() {
      assertThat(validator.validate(new MessageResponse(MESSAGE))).isEmpty();
    }

    @Test
    @DisplayName("a blank message is rejected")
    void aBlankMessageIsRejected() {
      Set<ConstraintViolation<MessageResponse>> violations =
          validator.validate(new MessageResponse("   "));

      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).isEqualTo("message cannot be blank");
    }

    @Test
    @DisplayName("a null message is rejected")
    void aNullMessageIsRejected() {
      assertThat(validator.validate(new MessageResponse(null))).hasSize(1);
    }
  }
}
