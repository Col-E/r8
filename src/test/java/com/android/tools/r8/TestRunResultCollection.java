// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.hamcrest.Matcher;

/** Checking container for checking the same properties of multiple run results. */
public abstract class TestRunResultCollection<
        C extends Enum<C>, RR extends TestRunResultCollection<C, RR>>
    extends TestRunResult<RR> {

  private final List<Pair<C, TestRunResult<?>>> runs;

  public TestRunResultCollection(List<Pair<C, TestRunResult<?>>> runs) {
    this.runs = runs;
  }

  private RR forEach(Consumer<TestRunResult<?>> fn) {
    runs.forEach(r -> fn.accept(r.getSecond()));
    return self();
  }

  @Override
  public RR assertSuccess() {
    return forEach(TestRunResult::assertSuccess);
  }

  @Override
  public RR assertFailure() {
    return forEach(TestRunResult::assertFailure);
  }

  @Override
  public RR assertStdoutMatches(Matcher<String> matcher) {
    return forEach(r -> r.assertStdoutMatches(matcher));
  }

  @Override
  public RR assertStderrMatches(Matcher<String> matcher) {
    return forEach(r -> r.assertStderrMatches(matcher));
  }

  @Override
  public <E extends Throwable> RR inspect(ThrowingConsumer<CodeInspector, E> consumer)
      throws IOException, ExecutionException, E {
    return inspectIf(Predicates.alwaysTrue(), consumer);
  }

  public RR applyIf(Predicate<C> filter, Consumer<TestRunResult<?>> fn) {
    for (Pair<C, TestRunResult<?>> run : runs) {
      if (filter.test(run.getFirst())) {
        fn.accept(run.getSecond());
      }
    }
    return self();
  }

  public <E extends Throwable> RR inspectIf(
      Predicate<C> filter, ThrowingConsumer<CodeInspector, E> consumer)
      throws IOException, ExecutionException, E {
    for (Pair<C, TestRunResult<?>> run : runs) {
      if (filter.test(run.getFirst())) {
        run.getSecond().inspect(consumer);
      }
    }
    return self();
  }

  @Override
  public RR disassemble() throws IOException, ExecutionException {
    for (Pair<C, TestRunResult<?>> run : runs) {
      String name = run.getFirst().name();
      System.out.println(name + " " + Strings.repeat("=", 80 - name.length() - 1));
      run.getSecond().disassemble();
    }
    return self();
  }
}
