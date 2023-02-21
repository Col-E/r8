// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NarrowingWithoutSubtypingTest extends TestBase {

  private final TestParameters parameters;
  private final boolean readStackMap;

  @Parameters(name = "{0}, read stack map: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  public NarrowingWithoutSubtypingTest(TestParameters parameters, boolean readStackMap) {
    this.parameters = parameters;
    this.readStackMap = readStackMap;
  }

  @Test
  public void test() throws Exception {
    D8TestBuilder d8TestBuilder =
        testForD8()
            .addInnerClasses(NarrowingWithoutSubtypingTest.class)
            .addOptionsModification(
                options -> {
                  options.testing.readInputStackMaps = readStackMap;
                  options.testing.enableNarrowAndWideningingChecksInD8 = true;
                  options.testing.noLocalsTableOnInput = true;
                })
            .setMinApi(parameters);
    if (readStackMap) {
      d8TestBuilder
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello world!");
    } else {
      // TODO(b/169120386): We should not be narrowing in D8.
      assertThrows(
          CompilationFailedException.class,
          () -> {
            d8TestBuilder.compileWithExpectedDiagnostics(
                diagnostics ->
                    diagnostics.assertAllErrorsMatch(
                        diagnosticMessage(
                            containsString("java.lang.AssertionError: During NARROWING"))));
          });
    }
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
