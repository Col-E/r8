// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.throwing;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ThrowingTestRunner extends ExamplesTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public ThrowingTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return Throwing.class;
  }

  @Override
  public List<Class<?>> getTestClasses() throws Exception {
    return ImmutableList.of(
        getMainClass(), Overloaded.class, RenamedClass.class, Throwing.Nested.class);
  }

  @Override
  public String getExpected() {
    return StringUtils.lines(
        "FRAME: com.android.tools.r8.examples.throwing.Throwing.throwAtFistLine(Throwing.java:115)",
        "FRAME: com.android.tools.r8.examples.throwing.Throwing.main(Throwing.java:19)",
        "FRAME: com.android.tools.r8.examples.throwing.Throwing.throwInMiddle(Throwing.java:123)",
        "FRAME: com.android.tools.r8.examples.throwing.Throwing.main(Throwing.java:24)",
        "FRAME:"
            + " com.android.tools.r8.examples.throwing.Throwing.throwAfterMultiInline(Throwing.java:135)",
        "FRAME: com.android.tools.r8.examples.throwing.Throwing.main(Throwing.java:29)",
        "FRAME: com.android.tools.r8.examples.throwing.Throwing.main(Throwing.java:41)",
        "FRAME:"
            + " com.android.tools.r8.examples.throwing.Throwing$Nested.justThrow(Throwing.java:207)",
        "FRAME: com.android.tools.r8.examples.throwing.Throwing.main(Throwing.java:49)",
        "20Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Incremented by 10.",
        "FRAME:"
            + " com.android.tools.r8.examples.throwing.Throwing.throwInAFunctionThatIsNotInlinedAndCalledTwice(Throwing.java:145)",
        "FRAME: com.android.tools.r8.examples.throwing.Throwing.main(Throwing.java:65)",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Incremented by 10.",
        "FRAME:"
            + " com.android.tools.r8.examples.throwing.Throwing.throwInAFunctionThatIsNotInlinedAndCalledTwice(Throwing.java:145)",
        "FRAME: com.android.tools.r8.examples.throwing.Throwing.main(Throwing.java:71)",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Incremented by 10.",
        "FRAME:"
            + " com.android.tools.r8.examples.throwing.Throwing.anotherThrowingMethodToInline(Throwing.java:151)",
        "FRAME:"
            + " com.android.tools.r8.examples.throwing.Throwing.aFunctionThatCallsAnInlinedMethodThatThrows(Throwing.java:164)",
        "FRAME: com.android.tools.r8.examples.throwing.Throwing.main(Throwing.java:77)",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Increment by one!",
        "Incremented by 10.",
        "FRAME:"
            + " com.android.tools.r8.examples.throwing.Throwing.yetAnotherThrowingMethodToInline(Throwing.java:170)",
        "FRAME:"
            + " com.android.tools.r8.examples.throwing.Throwing.anotherFunctionThatCallsAnInlinedMethodThatThrows(Throwing.java:183)",
        "FRAME: com.android.tools.r8.examples.throwing.Throwing.main(Throwing.java:83)",
        "FRAME:"
            + " com.android.tools.r8.examples.throwing.Throwing.aFunctionsThatThrowsBeforeAnInlinedMethod(Throwing.java:188)",
        "FRAME: com.android.tools.r8.examples.throwing.Throwing.main(Throwing.java:89)");
  }

  @Test
  public void testDesugaring() throws Exception {
    runTestDesugaring();
  }

  @Test
  public void testR8() throws Exception {
    // The expected output includes the reflected frames so disable all optimization.
    runTestR8(b -> b.addDontOptimize().addDontObfuscate().addKeepAllAttributes());
  }

  @Test
  public void testDebug() throws Exception {
    runTestDebugComparator();
  }
}
