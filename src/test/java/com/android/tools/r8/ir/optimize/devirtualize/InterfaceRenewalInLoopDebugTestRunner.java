// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.devirtualize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import org.junit.Test;

public class InterfaceRenewalInLoopDebugTestRunner extends DebugTestBase {
  private static Class<?> MAIN = InterfaceRenewalInLoopDebugTest.class;
  private static Class<?> IMPL = OneUniqueImplementer.class;

  @Test
  public void test() throws Throwable {
    R8TestCompileResult result = testForR8(Backend.CF)
        .setMode(CompilationMode.DEBUG)
        .addProgramClasses(TestInterface.class, IMPL, MAIN)
        .addKeepMainRule(MAIN)
        .addKeepRules(ImmutableList.of("-keepattributes SourceFile,LineNumberTable"))
        .noMinification()
        .compile();

    CodeInspector inspector = result.inspector();
    ClassSubject mainSubject = inspector.clazz(MAIN);
    assertThat(mainSubject, isPresent());
    MethodSubject methodSubject = mainSubject.uniqueMethodWithName("booRunner");
    assertThat(methodSubject, isPresent());
    verifyDevirtualized(methodSubject);

    DebugTestConfig config = result.debugConfig();
    runDebugTest(config, MAIN.getCanonicalName(),
        breakpoint(MAIN.getCanonicalName(), "booRunner", 37),
        run(),
        // At line 37
        checkLocal("i"),
        checkLocal("local"),
        breakpoint(MAIN.getCanonicalName(), "booRunner", 40),
        run(),
        // At line 40
        checkLocal("i"),
        checkLocal("local"),
        // Visiting line 37 and 40 again
        run(),
        checkLocal("i"),
        checkLocal("local"),
        run(),
        checkLocal("i"),
        checkLocal("local"),
        // One more time, as boos = {"a", "b", "c"}
        run(),
        checkLocal("i"),
        checkLocal("local"),
        run(),
        checkLocal("i"),
        checkLocal("local"),
        // Now run until the program finishes.
        run());
  }

  private void verifyDevirtualized(MethodSubject method) {
    long virtualCallCount = Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvokeVirtual()) {
        return isDevirtualizedCall(instructionSubject.getMethod());
      }
      return false;
    })).count();
    long checkCastCount = Streams.stream(method.iterateInstructions(instructionSubject -> {
      return instructionSubject.isCheckCast(IMPL.getTypeName());
    })).count();
    assertTrue(virtualCallCount > 0);
    // Make sure that all devirtualized calls don't share check-casted receiver.
    // Rather, it should use its own check-casted receiver to not pass the local.
    assertEquals(virtualCallCount, checkCastCount);
  }

  private static boolean isDevirtualizedCall(DexMethod method) {
    return method.holder.toSourceString().equals(IMPL.getTypeName())
        && method.getArity() == 0
        && method.proto.returnType.isVoidType()
        && method.name.toString().equals("foo");
  }

}
