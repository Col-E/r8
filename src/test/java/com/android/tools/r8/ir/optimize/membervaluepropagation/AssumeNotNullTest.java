// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssumeNotNullTest extends TestBase {

  private final String flavor;
  private final TestParameters parameters;

  @Parameters(name = "{1}, flavor: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of("assumenosideeffects", "assumevalues"),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public AssumeNotNullTest(String flavor, TestParameters parameters) {
    this.flavor = flavor;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-" + flavor + " class " + Factory.class.getTypeName() + " {",
            "  java.lang.Object create() return _NONNULL_;",
            "}",
            "-" + flavor + " class " + Singleton.class.getTypeName() + " {",
            "  java.lang.Object INSTANCE return _NONNULL_;",
            "}")
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
              assertThat(mainMethodSubject, isPresent());
              if (flavor.equals("assumenosideeffects")) {
                // With -assumenosideeffects, the method should become empty.
                assertTrue(
                    mainMethodSubject
                        .streamInstructions()
                        .allMatch(InstructionSubject::isReturnVoid));
              } else {
                // With -assumevalues, the Singleton.INSTANCE access should remain along with the
                // Factory.create() invoke.
                ClassSubject factoryClassSubject = inspector.clazz(Factory.class);
                assertThat(factoryClassSubject, isPresent());

                ClassSubject singletonClassSubject = inspector.clazz(Singleton.class);
                assertThat(singletonClassSubject, isPresent());

                assertEquals(
                    2,
                    mainMethodSubject
                        .streamInstructions()
                        .filter(
                            instruction -> instruction.isFieldAccess() || instruction.isInvoke())
                        .count());
                assertTrue(
                    mainMethodSubject
                        .streamInstructions()
                        .anyMatch(
                            instruction ->
                                instruction.isStaticGet()
                                    && instruction.getField().getHolderType()
                                        == singletonClassSubject.getDexProgramClass().getType()));
                assertTrue(
                    mainMethodSubject
                        .streamInstructions()
                        .filter(InstructionSubject::isInvoke)
                        .anyMatch(
                            instruction ->
                                instruction.getMethod().getHolderType()
                                    == factoryClassSubject.getDexProgramClass().getType()));
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  static class Main {

    public static void main(String[] args) {
      if (Singleton.INSTANCE == null) {
        System.out.println("Foo");
      }
      if (Factory.create() == null) {
        System.out.println("Bar");
      }
    }
  }

  static class Factory {

    @NeverInline
    public static Object create() {
      return System.currentTimeMillis() > 0 ? new Object() : null;
    }
  }

  static class Singleton {

    public static Object INSTANCE = System.currentTimeMillis() > 0 ? new Object() : null;
  }
}
