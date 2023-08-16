// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.ConsumerUtils.emptyThrowingConsumer;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.hamcrest.Matcher;

/**
 * Abstract base for checking the result of executing (test) program(s).
 *
 * <p>All methods defined on this base must generalize to allow checking of multiple run results
 * simultaneously. For methods on single runs see the SingleTestRunResult subclass.
 */
public abstract class TestRunResult<RR extends TestRunResult<RR>> {

  abstract RR self();

  public abstract RR assertSuccess();

  public abstract RR assertStdoutMatches(Matcher<String> matcher);

  public abstract RR assertFailure();

  public abstract RR assertStderrMatches(Matcher<String> matcher);

  public abstract <E extends Throwable> RR inspect(ThrowingConsumer<CodeInspector, E> consumer)
      throws IOException, ExecutionException, E;

  public abstract <E extends Throwable> RR inspectFailure(
      ThrowingConsumer<CodeInspector, E> consumer) throws IOException, E;

  public abstract RR disassemble() throws IOException, ExecutionException;

  public <E extends Throwable> RR apply(ThrowingConsumer<RR, E> fn) throws E {
    fn.accept(self());
    return self();
  }

  public <T extends Throwable> RR applyIf(boolean condition, ThrowingConsumer<RR, T> thenConsumer)
      throws T {
    return applyIf(condition, thenConsumer, emptyThrowingConsumer());
  }

  public <S extends Throwable, T extends Throwable> RR applyIf(
      boolean condition, ThrowingConsumer<RR, S> thenConsumer, ThrowingConsumer<RR, T> elseConsumer)
      throws S, T {
    return applyIf(
        condition,
        thenConsumer,
        true,
        elseConsumer,
        r -> {
          assert false;
        });
  }

  public <S extends Throwable, T extends Throwable, U extends Throwable> RR applyIf(
      boolean condition1,
      ThrowingConsumer<RR, S> thenConsumer1,
      boolean condition2,
      ThrowingConsumer<RR, T> thenConsumer2,
      ThrowingConsumer<RR, U> elseConsumer)
      throws S, T, U {
    return applyIf(
        condition1,
        thenConsumer1,
        condition2,
        thenConsumer2,
        true,
        elseConsumer,
        r -> {
          assert false;
        });
  }

  public <S extends Throwable, T extends Throwable, U extends Throwable, V extends Throwable>
      RR applyIf(
          boolean condition1,
          ThrowingConsumer<RR, S> thenConsumer1,
          boolean condition2,
          ThrowingConsumer<RR, T> thenConsumer2,
          boolean condition3,
          ThrowingConsumer<RR, U> thenConsumer3,
          ThrowingConsumer<RR, V> elseConsumer)
          throws S, T, U, V {
    if (condition1) {
      thenConsumer1.accept(self());
    } else if (condition2) {
      thenConsumer2.accept(self());
    } else if (condition3) {
      thenConsumer3.accept(self());
    } else {
      elseConsumer.accept(self());
    }
    return self();
  }

  public <S> S map(Function<RR, S> fn) {
    return fn.apply(self());
  }

  public RR assertSuccessWithOutputThatMatches(Matcher<String> matcher) {
    assertStdoutMatches(matcher);
    return assertSuccess();
  }

  public RR assertSuccessWithOutput(String expected) {
    return assertSuccessWithOutputThatMatches(is(expected));
  }

  public RR assertSuccessWithOutputIf(boolean condition, String expected) {
    if (condition) {
      return assertSuccessWithOutput(expected);
    }
    return self();
  }

  public RR assertSuccessWithEmptyOutput() {
    return assertSuccessWithOutput("");
  }

  public RR assertSuccessWithOutputLines(String... expected) {
    return assertSuccessWithOutputLines(Arrays.asList(expected));
  }

  public RR assertSuccessWithOutputLinesIf(boolean condition, String... expected) {
    return assertSuccessWithOutputLinesIf(condition, Arrays.asList(expected));
  }

  public RR assertSuccessWithOutputLinesIf(boolean condition, List<String> expected) {
    if (condition) {
      return assertSuccessWithOutputLines(expected);
    }
    return self();
  }

  public RR assertSuccessWithOutputLines(List<String> expected) {
    return assertSuccessWithOutput(StringUtils.lines(expected));
  }

  public RR assertFailureWithErrorThatMatches(Matcher<String> matcher) {
    assertStderrMatches(matcher);
    return assertFailure();
  }

  public RR assertFailureWithErrorThatMatchesIf(boolean condition, Matcher<String> matcher) {
    if (condition) {
      assertStderrMatches(matcher);
      return assertFailure();
    }
    return self();
  }

  public RR assertFailureWithOutput(String expected) {
    assertStdoutMatches(is(expected));
    return assertFailure();
  }

  public RR assertFailureWithOutputThatMatches(Matcher<String> matcher) {
    assertStdoutMatches(matcher);
    return assertFailure();
  }

  public RR assertFailureWithErrorThatThrows(Class<? extends Throwable> expectedError) {
    return assertFailureWithErrorThatMatches(containsString(expectedError.getName()));
  }

  public RR assertFailureWithErrorThatThrowsIf(
      boolean condition, Class<? extends Throwable> expectedError) {
    if (condition) {
      return assertFailureWithErrorThatThrows(expectedError);
    }
    return self();
  }
}
