// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NarrowingWithoutSubtypingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public NarrowingWithoutSubtypingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test(expected = CompilationFailedException.class)
  public void test() throws Exception {
    testForD8()
        .addInnerClasses(NarrowingWithoutSubtypingTest.class)
        .addOptionsModification(
            options -> {
              options.testing.enableNarrowingChecksInD8 = true;
              options.testing.noLocalsTableOnInput = true;
            })
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    static Greeter field = new Greeter();

    public static void main(String[] args) {
      Greeter greeter;
      if (System.currentTimeMillis() >= 0) {
        field = System.currentTimeMillis() >= 0 ? new GreeterImpl() : null;
        // Redundant field load elimination will replace the field load in the next line by a value
        // of type GreeterImpl.
        greeter = field;
      } else {
        greeter = new Greeter();
      }
      // The phi that is inserted at this point will originally have type Greeter, but after
      // redundant field load elimination, the type of the phi will be the join of Greeter and
      // GreeterImpl, which is Object when we do not have subtyping information.
      greeter.greet();
    }
  }

  static class Greeter {

    void greet() {}
  }

  static class GreeterImpl extends Greeter {

    @Override
    void greet() {
      System.out.println("Hello world!");
    }
  }
}
