// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.hamcrest.Matcher;

public class L8TestRunResult extends TestRunResult<L8TestRunResult> {

  public L8TestRunResult() {
    throw new Unimplemented();
  }

  @Override
  L8TestRunResult self() {
    throw new Unimplemented();
  }

  @Override
  public L8TestRunResult assertSuccess() {
    throw new Unimplemented();
  }

  @Override
  public L8TestRunResult assertStdoutMatches(Matcher<String> matcher) {
    throw new Unimplemented();
  }

  @Override
  public L8TestRunResult assertFailure() {
    throw new Unimplemented();
  }

  @Override
  public L8TestRunResult assertStderrMatches(Matcher<String> matcher) {
    throw new Unimplemented();
  }

  @Override
  public <E extends Throwable> L8TestRunResult inspect(ThrowingConsumer<CodeInspector, E> consumer)
      throws E {
    throw new Unimplemented();
  }

  @Override
  public <E extends Throwable> L8TestRunResult inspectFailure(
      ThrowingConsumer<CodeInspector, E> consumer) {
    throw new Unimplemented();
  }

  @Override
  public L8TestRunResult disassemble() {
    throw new Unimplemented();
  }
}
