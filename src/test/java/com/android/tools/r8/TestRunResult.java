// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
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

  public abstract RR disassemble() throws IOException, ExecutionException;

  public RR apply(Consumer<RR> fn) {
    fn.accept(self());
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

  public RR assertSuccessWithEmptyOutput() {
    return assertSuccessWithOutput("");
  }

  public RR assertSuccessWithOutputLines(String... expected) {
    return assertSuccessWithOutputLines(Arrays.asList(expected));
  }

  public RR assertSuccessWithOutputLines(List<String> expected) {
    return assertSuccessWithOutput(StringUtils.lines(expected));
  }

  public RR assertFailureWithErrorThatMatches(Matcher<String> matcher) {
    assertStderrMatches(matcher);
    return assertFailure();
  }

  public RR assertFailureWithOutput(String expected) {
    assertStdoutMatches(is(expected));
    return assertFailure();
  }

  public RR assertFailureWithErrorThatThrows(Class<? extends Throwable> expectedError) {
    return assertFailureWithErrorThatMatches(containsString(expectedError.getName()));
  }
}
