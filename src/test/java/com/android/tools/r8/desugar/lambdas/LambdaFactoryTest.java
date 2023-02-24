// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaFactoryTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("1", "2", "3", "4.0", "5");

  private boolean isLambdaFactoryMethod(MethodSubject method) {
    return method.isSynthetic() && method.isStatic() && method.getFinalName().equals("create");
  }

  private boolean isInvokingLambdaFactoryMethod(InstructionSubject instruction) {
    return instruction.isInvokeStatic()
        && SyntheticItemsTestUtils.isExternalSynthetic(
            instruction.getMethod().getHolderType().asClassReference())
        && instruction.getMethod().getName().toString().equals("create");
  }

  private void inspectDesugared(CodeInspector inspector) {
    inspector.forAllClasses(
        clazz -> {
          if (SyntheticItemsTestUtils.isExternalSynthetic(clazz.getFinalReference())) {
            assertTrue(clazz.allMethods().stream().anyMatch(this::isLambdaFactoryMethod));
          }
        });
    assertEquals(
        3,
        inspector
            .clazz(TestClass.class)
            .mainMethod()
            .streamInstructions()
            .filter(this::isInvokingLambdaFactoryMethod)
            .count());
  }

  private void inspectNotDesugared(CodeInspector inspector) {
    inspector.forAllClasses(
        clazz -> {
          if (SyntheticItemsTestUtils.isExternalSynthetic(clazz.getFinalReference())) {
            assertTrue(clazz.allMethods().stream().noneMatch(this::isLambdaFactoryMethod));
          }
        });
    assertEquals(
        0,
        inspector
            .clazz(TestClass.class)
            .mainMethod()
            .streamInstructions()
            .filter(this::isInvokingLambdaFactoryMethod)
            .count());
  }

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(
            parameters,
            options -> {
              options.testing.alwaysGenerateLambdaFactoryMethods = true;
            })
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            DesugarTestConfiguration::isDesugared,
            r -> {
              try {
                r.inspect(this::inspectDesugared);
              } catch (Exception e) {
                fail();
              }
            },
            r -> {
              try {
                r.inspect(this::inspectNotDesugared);
              } catch (Exception e) {
                fail();
              }
            })
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(
            inspector -> {
              if (parameters.isDexRuntime()) {
                // Lambdas are fully inlined when desugaring.
                assertEquals(1, inspector.allClasses().size());
                assertEquals(1, inspector.clazz(TestClass.class).allMethods().size());
              }
            })
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  interface MyConsumer<T> {
    void create(T o);
  }

  interface MyTriConsumer<T, U, V> {
    void accept(T o1, U o2, V o3);
  }

  static class TestClass {

    public static void greet() {
      System.out.println("1");
    }

    public static void greet(MyConsumer<String> consumer) {
      consumer.create("2");
    }

    public static void greetTri(long l, double d, String s) {
      System.out.println(l);
      System.out.println(d);
      System.out.println(s);
    }

    public static void main(String[] args) {
      ((Runnable) TestClass::greet).run();
      greet(System.out::println);
      ((MyTriConsumer<Long, Double, String>) TestClass::greetTri).accept(3L, 4.0, "5");
    }
  }
}
