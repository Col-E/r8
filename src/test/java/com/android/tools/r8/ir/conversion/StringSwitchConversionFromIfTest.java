// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.List;
import java.util.Objects;
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
    return buildParameters(BooleanUtils.values(), getTestParameters().withAllRuntimes().build());
  }

  public StringSwitchConversionFromIfTest(
      boolean enableStringSwitchConversion, TestParameters parameters) {
    this.enableStringSwitchConversion = enableStringSwitchConversion;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    // TODO(b/135559645): Add support for identifying string-switch instructions in the generated
    //  bytecode.
    assumeFalse(enableStringSwitchConversion);

    testForR8(parameters.getBackend())
        .addInnerClasses(StringSwitchConversionFromIfTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> {
              assert !options.enableStringSwitchConversion;
              options.enableStringSwitchConversion = enableStringSwitchConversion;
            })
        .enableInliningAnnotations()
        // TODO(b/135560746): Add support for treating the keys of a string-switch instruction as an
        //  identifier name string.
        .noMinification()
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(TestClass.class);
              assertThat(classSubject, isPresent());

              List<String> methodNames =
                  ImmutableList.of(
                      "test",
                      "testWithMultipleHashCodeInvocations",
                      "testWithSwitch",
                      "testNullCheckIsPreserved");
              for (String methodName : methodNames) {
                MethodSubject methodSubject = classSubject.uniqueMethodWithName(methodName);
                assertThat(methodSubject, isPresent());

                DexItemFactory dexItemFactory = new DexItemFactory();
                InternalOptions options = new InternalOptions(dexItemFactory, new Reporter());
                options.enableStringSwitchConversion = enableStringSwitchConversion;

                IRCode code = methodSubject.buildIR(options);
                List<Value> hashCodeValues =
                    Streams.stream(code.instructions())
                        .filter(instruction -> isInvokeStringHashCode(instruction, dexItemFactory))
                        .map(Instruction::asInvokeVirtual)
                        .map(Instruction::outValue)
                        .collect(Collectors.toList());

                // There should be at least one call to `int String.hashCode()`, since the
                // instruction may throw. This holds even if the string-switch instruction is
                // compiled to a sequence of `if (x.equals("..."))` instructions that do not even
                // use the hash code.
                assertNotEquals(0, hashCodeValues.size());

                // We indirectly verify that the string-switch instructions have been identified
                // by checking that none of the hash codes are used for anything. Note that this
                // only holds with a backend that compiles string-switch instruction to
                // `if (x.equals("..."))` instructions that do not use the hash code for anything.
                assertEquals(
                    methodName,
                    enableStringSwitchConversion,
                    hashCodeValues.stream().filter(Objects::nonNull).noneMatch(Value::isUsed));

                // Check that the IR has exactly one StringSwitch instruction iff string-switch
                // conversion is enabled.
                long numberOfStringSwitchInstructions =
                    Streams.stream(code.instructions()).filter(Instruction::isStringSwitch).count();
                assertEquals(enableStringSwitchConversion, numberOfStringSwitchInstructions == 1);
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
    String signature = dexItemFactory.stringMethods.hashCode.toSourceString();
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
      test(A.class);
      test(B.class);
      testWithMultipleHashCodeInvocations(A.class);
      testWithMultipleHashCodeInvocations(B.class);
      testWithSwitch(A.class);
      testWithSwitch(B.class);

      try {
        testNullCheckIsPreserved(System.currentTimeMillis() >= 0 ? null : A.class);
      } catch (NullPointerException e) {
        System.out.println("Caught NPE");
      }
    }

    @NeverInline
    private static void test(Class<?> clazz) {
      String className = clazz.getName();
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
        System.out.print("He");
      } else if (result == 1) {
        System.out.print("l");
      }
    }

    @NeverInline
    private static void testWithMultipleHashCodeInvocations(Class<?> clazz) {
      String className = clazz.getName();
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
        System.out.print("l");
      } else if (result == 1) {
        System.out.print("o");
      }
    }

    @NeverInline
    private static void testWithSwitch(Class<?> clazz) {
      String className = clazz.getName();
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
          System.out.print(" world");
          break;
        case 1:
          System.out.println("!");
          break;
        default:
          // Ignore.
      }
    }

    @NeverInline
    private static void testNullCheckIsPreserved(Class<?> clazz) {
      switch (clazz.getName()) {
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
