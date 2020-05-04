// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FieldWithDefaultValueAssignmentAfterDefaultsOptimizationTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public FieldWithDefaultValueAssignmentAfterDefaultsOptimizationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(FieldWithDefaultValueAssignmentAfterDefaultsOptimizationTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> options.testing.waveModifier = this::waveModifier)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42");
  }

  private void waveModifier(Deque<Map<DexEncodedMethod, ProgramMethod>> waves) {
    Map<DexEncodedMethod, ProgramMethod> initialWave = waves.getFirst();
    Optional<ProgramMethod> printFieldMethod =
        initialWave.values().stream()
            .filter(
                method -> method.getDefinition().method.name.toSourceString().equals("printField"))
            .findFirst();
    assertTrue(printFieldMethod.isPresent());
    initialWave.remove(printFieldMethod.get().getDefinition());

    Map<DexEncodedMethod, ProgramMethod> lastWave = new IdentityHashMap<>();
    lastWave.put(printFieldMethod.get().getDefinition(), printFieldMethod.get());
    waves.addLast(lastWave);
  }

  static class TestClass {

    static int field = 42;

    static {
      printField();
      field = 0;
    }

    @NeverInline
    static void printField() {
      System.out.println(field); // Should print 42.
    }

    public static void main(String[] args) {}
  }
}
