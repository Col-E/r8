// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.hamcrest.Matcher;

/** Checking container for checking the same properties of multiple run results. */
public class TestRunResultCollection extends TestRunResult<TestRunResultCollection> {

  public static TestRunResultCollection create(List<Pair<String, TestRunResult<?>>> runs) {
    assert !runs.isEmpty();
    return new TestRunResultCollection(runs);
  }

  private final List<Pair<String, TestRunResult<?>>> runs;

  public TestRunResultCollection(List<Pair<String, TestRunResult<?>>> runs) {
    this.runs = runs;
  }

  @Override
  TestRunResultCollection self() {
    return this;
  }

  private TestRunResultCollection forEach(Consumer<TestRunResult<?>> fn) {
    runs.forEach(r -> fn.accept(r.getSecond()));
    return self();
  }

  @Override
  public TestRunResultCollection assertSuccess() {
    return forEach(TestRunResult::assertSuccess);
  }

  @Override
  public TestRunResultCollection assertFailure() {
    return forEach(TestRunResult::assertFailure);
  }

  @Override
  public TestRunResultCollection assertStdoutMatches(Matcher<String> matcher) {
    return forEach(r -> r.assertStdoutMatches(matcher));
  }

  @Override
  public TestRunResultCollection assertStderrMatches(Matcher<String> matcher) {
    return forEach(r -> r.assertStderrMatches(matcher));
  }

  @Override
  public <E extends Throwable> TestRunResultCollection inspect(
      ThrowingConsumer<CodeInspector, E> consumer) throws IOException, ExecutionException, E {
    for (Pair<String, TestRunResult<?>> run : runs) {
      run.getSecond().inspect(consumer);
    }
    return self();
  }

  @Override
  public TestRunResultCollection disassemble() throws IOException, ExecutionException {
    for (Pair<String, TestRunResult<?>> run : runs) {
      String name = run.getFirst();
      System.out.println(name + " " + Strings.repeat("=", 80 - name.length() - 1));
      run.getSecond().disassemble();
    }
    return self();
  }
}
