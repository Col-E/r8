// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringSwitchConversionFromIfTest extends TestBase {

  private final boolean enableStringSwitchConversion;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, enable string switch conversion: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public StringSwitchConversionFromIfTest(
      boolean enableStringSwitchConversion, TestParameters parameters) {
    this.enableStringSwitchConversion = enableStringSwitchConversion;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(StringSwitchConversionFromIfTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> options.enableStringSwitchConversion = enableStringSwitchConversion)
        .enableInliningAnnotations()
        // TODO(b/135560746): Add support for treating the keys of a string-switch instruction as an
        //  identifier name string.
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(TestClass.class);
              assertThat(classSubject, isPresent());

              List<String> methodNames =
                  ImmutableList.of(
                      "test",
                      "testWithDeadStringIdComparisons",
                      "testWithMultipleHashCodeInvocations",
                      "testWithSwitch",
                      "testNullCheckIsPreserved");
              for (String methodName : methodNames) {
                MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName(methodName);
                assertThat(methodSubject, isPresent());
                IRCode code = methodSubject.buildIR();
                List<Value> hashCodeValues =
                    Streams.stream(code.instructions())
                        .filter(
                            instruction ->
                                isInvokeStringHashCode(instruction, inspector.getFactory()))
                        .map(Instruction::asInvokeVirtual)
                        .map(Instruction::outValue)
                        .collect(Collectors.toList());

                // There should be at least one call to `int String.hashCode()`, since the
                // instruction may throw. This holds even if the string-switch instruction is
                // compiled to a sequence of `if (x.equals("..."))` instructions that do not even
                // use the hash code.
                assertTrue(
                    code.collectArguments().get(0).uniqueUsers().stream()
                        .filter(Instruction::isInvokeMethod)
                        .map(Instruction::asInvokeMethod)
                        .map(invoke -> invoke.getInvokedMethod().getName().toSourceString())
                        .anyMatch(
                            name ->
                                name.equals("getClass")
                                    || name.equals("hashCode")
                                    || name.equals("requireNonNull")));
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!", "Caught NPE");
  }

  @Test
  public void testCorrectnessOfConstants() {
    assertEquals(A.class.getTypeName(), TestClass.NAME_1);
    assertEquals(A.class.getTypeName().hashCode(), TestClass.HASH_1);
    assertEquals(B.class.getTypeName(), TestClass.NAME_2);
    assertEquals(B.class.getTypeName().hashCode(), TestClass.HASH_2);
  }

  private static boolean isInvokeStringHashCode(
      Instruction instruction, DexItemFactory dexItemFactory) {
    // Intentionally using toSourceString() because `instruction.getInvokedMethod()` belongs to
    // another factory than the given `dexItemFactory`.
    String signature = dexItemFactory.stringMembers.hashCode.toSourceString();
    return instruction.isInvokeVirtual()
        && instruction.asInvokeVirtual().getInvokedMethod().toSourceString().equals(signature);
  }

  static class TestClass {

    private static final String NAME_1 =
        "com.android.tools.r8.ir.conversion.StringSwitchConversionFromIfTest$A";
    private static final int HASH_1 = -1340982899;

    private static final String NAME_2 =
        "com.android.tools.r8.ir.conversion.StringSwitchConversionFromIfTest$B";
    private static final int HASH_2 = -1340982898;

    public static void main(String[] args) {
      test(toNullableClassName(A.class));
      test(toNullableClassName(B.class));
      testWithDeadStringIdComparisons(toNullableClassName(A.class));
      testWithDeadStringIdComparisons(toNullableClassName(B.class));
      testWithMultipleHashCodeInvocations(toNullableClassName(A.class));
      testWithMultipleHashCodeInvocations(toNullableClassName(B.class));
      testWithSwitch(toNullableClassName(A.class));
      testWithSwitch(toNullableClassName(B.class));

      try {
        testNullCheckIsPreserved(System.currentTimeMillis() >= 0 ? null : A.class.getName());
      } catch (NullPointerException e) {
        System.out.println("Caught NPE");
      }
    }

    static String toNullableClassName(Class<?> clazz) {
      return System.currentTimeMillis() > 0 ? clazz.getName() : null;
    }

    @NeverInline
    private static void test(String className) {
      int hashCode = className.hashCode();
      int result = -1;
      if (hashCode == HASH_1) {
        if (className.equals(NAME_1)) {
          result = 0;
        }
      } else if (hashCode == HASH_2) {
        if (className.equals(NAME_2)) {
          result = 1;
        }
      }
      if (result == 0) {
        System.out.print("H");
      } else if (result == 1) {
        System.out.print("e");
      }
    }

    @NeverInline
    private static void testWithDeadStringIdComparisons(String className) {
      int hashCode = className.hashCode();
      int result = -1;
      if (hashCode == HASH_1) {
        if (className.equals(NAME_1)) {
          result = 0;
        }
      } else if (hashCode == HASH_2) {
        if (className.equals(NAME_2)) {
          result = 1;
        }
      }
      if (result == 0) {
        System.out.print("l");
      } else {
        switch (result) {
          case 0:
            System.out.println("Unexpected");
            break;
          default:
            if (result == 0) {
              System.out.print("Unexpected");
            } else if (result == 1) {
              System.out.print("l");
            } else if (result == 0) {
              System.out.print("Unexpected");
            } else if (result == 1) {
              System.out.print("Unexpected");
            } else {
              switch (result) {
                case 0:
                  System.out.println("Unexpected");
                  break;
                case 1:
                  System.out.println("Unexpected");
                  break;
              }
            }
            break;
        }
      }
    }

    @NeverInline
    private static void testWithMultipleHashCodeInvocations(String className) {
      int result = -1;
      if (className.hashCode() == HASH_1) {
        if (className.equals(NAME_1)) {
          result = 0;
        }
      } else if (className.hashCode() == HASH_2) {
        if (className.equals(NAME_2)) {
          result = 1;
        }
      }
      if (result == 0) {
        System.out.print("o");
      } else if (result == 1) {
        System.out.print(" ");
      }
    }

    @NeverInline
    private static void testWithSwitch(String className) {
      int result = -1;
      if (className.hashCode() == HASH_1) {
        if (className.equals(NAME_1)) {
          result = 0;
        }
      } else if (className.hashCode() == HASH_2) {
        if (className.equals(NAME_2)) {
          result = 1;
        }
      }
      switch (result) {
        case 0:
          System.out.print("world");
          break;
        case 1:
          System.out.println("!");
          break;
        default:
          // Ignore.
      }
    }

    @NeverInline
    private static void testNullCheckIsPreserved(String className) {
      switch (className) {
        case "A":
          System.out.println("Unexpected (A)");
          break;
        case "B":
          System.out.println("Unexpected (B)");
          break;
        case "C":
          System.out.println("Unexpected (C)");
          break;
        default:
          System.out.println("Unexpected (default)");
      }
    }
  }

  static class A {}

  static class B {}
}
