// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.devirtualize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceRenewalInLoopDebugTestRunner extends DebugTestBase {
  private static Class<?> MAIN = InterfaceRenewalInLoopDebugTest.class;
  private static Class<?> ITF = TestInterface.class;
  private static Class<?> IMPL = OneUniqueImplementer.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withSystemRuntime().build();
  }

  private final TestParameters parameters;

  public InterfaceRenewalInLoopDebugTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Throwable {
    R8TestCompileResult result =
        testForR8(parameters.getBackend())
            .debug()
            .addProgramClasses(ITF, IMPL, MAIN)
            .addKeepMainRule(MAIN)
            .enableNoVerticalClassMergingAnnotations()
            .compile();

    CodeInspector inspector = result.inspector();
    ClassSubject mainSubject = inspector.clazz(MAIN);
    assertThat(mainSubject, isPresent());
    MethodSubject methodSubject = mainSubject.uniqueMethodWithOriginalName("booRunner");
    assertThat(methodSubject, isPresent());
    verifyNotDevirtualized(methodSubject);

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

  private void verifyNotDevirtualized(MethodSubject method) {
    // Check all calls are on the interface. This used to check the replacement by the impl, but
    // debug soft-pinning now prohibits that.
    long virtualCallCount =
        method
            .streamInstructions()
            .filter(i -> i.isInvokeInterface() && isInterfaceCall(i.getMethod()))
            .count();
    assertTrue(virtualCallCount > 0);
    // Make sure that no calls share check-cast receivers.
    long checkCastCount =
        method.streamInstructions().filter(i -> i.isCheckCast(ITF.getTypeName())).count();
    assertEquals(virtualCallCount, checkCastCount);
  }

  // The calls to the interface actually remain as debug implies dontoptimize.
  private static boolean isInterfaceCall(DexMethod method) {
    return method.holder.toSourceString().equals(ITF.getTypeName())
        && method.getArity() == 0
        && method.proto.returnType.isVoidType()
        && method.name.toString().equals("foo");
  }

}
