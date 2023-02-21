// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ObjectsToStringOnLibraryClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ObjectsToStringOnLibraryClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
              assertThat(mainMethodSubject, isPresent());
              assertTrue(
                  mainMethodSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isInvoke)
                      .allMatch(
                          x ->
                              x.getMethod()
                                  .getName()
                                  .toSourceString()
                                  .equals("currentTimeMillis")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  static class Main {

    public static void main(String[] args) {
      boolean unknown = System.currentTimeMillis() > 0;

      // Boolean.
      Objects.toString(unknown ? Boolean.FALSE : null);
      Objects.toString(unknown ? Boolean.TRUE : null);

      // Byte.
      Objects.toString(unknown ? Byte.valueOf((byte) 0) : null);

      // Char.
      Objects.toString(unknown ? Character.valueOf((char) 0) : null);

      // Double.
      Objects.toString(unknown ? Double.valueOf(0) : null);

      // Float.
      Objects.toString(unknown ? Float.valueOf(0) : null);

      // Integer.
      Objects.toString(unknown ? Integer.valueOf(0) : null);

      // Long.
      Objects.toString(unknown ? Long.valueOf(0) : null);

      // Short.
      Objects.toString(unknown ? Short.valueOf((short) 0) : null);

      // String.
      Objects.toString(unknown ? "null" : null);
      Objects.toString(unknown ? new String("null") : null);

      // StringBuffer, StringBuilder.
      Objects.toString(unknown ? new StringBuffer() : null);
      Objects.toString(unknown ? new StringBuilder() : null);
    }
  }
}
