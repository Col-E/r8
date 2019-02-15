// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.JvmTestBuilder;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.desugar.DefaultLambdaWithUnderscoreThisTest.I;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.function.Function;
import org.junit.Test;

public class DefaultLambdaWithUnderscoreThisTestRunner extends DebugTestBase {

  final Class<?> CLASS = DefaultLambdaWithUnderscoreThisTest.class;

  final String EXPECTED = StringUtils
      .lines("stateful(My _this variable foo Another ___this variable)");

  private void runDebugger(DebugTestConfig config) throws Throwable {
    MethodReference main = Reference.methodFromMethod(CLASS.getMethod("main", String[].class));
    MethodReference stateful = Reference.methodFromMethod(I.class.getMethod("stateful"));
    Function<String, Command> checkThis = (String desugarThis) -> conditional((state) ->
        state.isCfRuntime()
            ? Collections.singletonList(checkLocal("this"))
            : ImmutableList.of(
                checkNoLocal("this"),
                checkLocal(desugarThis)));

    runDebugTest(config, CLASS,
        breakpoint(main, 22),
        run(),
        checkLine(22),
        stepInto(INTELLIJ_FILTER),
        checkLine(12),
        stepInto(INTELLIJ_FILTER),
        checkLine(13),
        // Desugaring will insert '__this' in place of 'this' here.
        checkThis.apply("__this"),
        checkLocal("_this"),
        breakpoint(main, 23),
        run(),
        checkLine(23),
        stepInto(INTELLIJ_FILTER),
        checkLine(14),
        stepInto(INTELLIJ_FILTER),
        checkLine(15),
        // Desugaring will insert '____this' in place of 'this' here.
        checkThis.apply("____this"),
        checkLocals("_this",  "___this"),
        run());
  }

  @Test
  public void testJvm() throws Throwable {
    JvmTestBuilder builder = testForJvm().addTestClasspath();
    builder.run(CLASS).assertSuccessWithOutput(EXPECTED);
    runDebugger(builder.debugConfig());
  }

  @Test
  public void testD8() throws Throwable {
    D8TestCompileResult compileResult = testForD8()
        .addProgramClassesAndInnerClasses(CLASS)
        .setMinApiThreshold(AndroidApiLevel.K)
        .compile();
    compileResult
        // TODO(b/123506120): Add .assertNoMessages()
        .run(CLASS)
        .assertSuccessWithOutput(EXPECTED);
    runDebugger(compileResult.debugConfig());
  }
}
