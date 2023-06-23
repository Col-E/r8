// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.enums;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests checking that Enum.ordinal() and Enum.name() calls on enum fields are rewritten into a
 * constant integer and string, respectively.
 */
@RunWith(Parameterized.class)
public class EnumOptimizationTest extends TestBase {

  private final boolean enableOptimization;
  private final TestParameters parameters;

  @Parameters(name = "{1}, enable enum optimization: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public EnumOptimizationTest(boolean enableOptimization, TestParameters parameters) {
    this.enableOptimization = enableOptimization;
    this.parameters = parameters;
  }

  private void configure(InternalOptions options) {
    options.enableEnumValueOptimization = enableOptimization;
    options.enableEnumSwitchMapRemoval = enableOptimization;
    options.enableEnumUnboxing = false;
  }

  @Test
  public void ordinals() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(Ordinals.class)
        .addKeepMainRule(Ordinals.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableSideEffectAnnotations()
        .addOptionsModification(this::configure)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspectOrdinals)
        .run(parameters.getRuntime(), Ordinals.class)
        .assertSuccessWithOutputLines("1", "1", "TWO1", "1", "11", "3", "1", "1", "1", "1");
  }

  private void inspectOrdinals(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Ordinals.class);
    assertTrue(clazz.isPresent());

    if (enableOptimization) {
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithOriginalName("simple"), 1);
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithOriginalName("local"), 1);
      // Even replaced ordinal is concatenated (and gone).
      assertOrdinalReplacedAndGone(clazz.uniqueMethodWithOriginalName("multipleUsages"));
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithOriginalName("inlined"), 1);
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithOriginalName("inSwitch"), 11);
      assertOrdinalReplacedWithConst(
          clazz.uniqueMethodWithOriginalName("differentTypeStaticField"), 1);
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithOriginalName("nonStaticGet"), 1);
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithOriginalName("nonValueStaticField"), 1);
    } else {
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithOriginalName("simple"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithOriginalName("local"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithOriginalName("multipleUsages"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithOriginalName("inlined"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithOriginalName("inSwitch"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithOriginalName("differentTypeStaticField"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithOriginalName("nonStaticGet"));
    }

    assertOrdinalWasNotReplaced(clazz.uniqueMethodWithOriginalName("libraryType"));
    assertOrdinalWasNotReplaced(clazz.uniqueMethodWithOriginalName("phi"));

    assertThat(clazz.uniqueMethodWithOriginalName("inlined2"), isAbsent());
  }

  @Test
  public void names() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(Names.class)
        .addKeepMainRule(Names.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableSideEffectAnnotations()
        .addOptionsModification(this::configure)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspectNames)
        .run(parameters.getRuntime(), Names.class)
        .assertSuccessWithOutputLines(
            "TWO", "TWO", "1TWO", "TWO", "SECONDS", "DOWN", "TWO", "TWO", "TWO");
  }

  private void inspectNames(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Names.class);
    assertTrue(clazz.isPresent());

    if (enableOptimization) {
      assertNameReplacedWithConst(clazz.uniqueMethodWithOriginalName("simple"), "TWO");
      assertNameReplacedWithConst(clazz.uniqueMethodWithOriginalName("local"), "TWO");
      assertNameReplacedWithConst(clazz.uniqueMethodWithOriginalName("multipleUsages"), "1TWO");
      assertNameReplacedWithConst(clazz.uniqueMethodWithOriginalName("inlined"), "TWO");
      assertNameReplacedWithConst(
          clazz.uniqueMethodWithOriginalName("differentTypeStaticField"), "DOWN");
      assertNameReplacedWithConst(clazz.uniqueMethodWithOriginalName("nonStaticGet"), "TWO");
      assertNameReplacedWithConst(clazz.uniqueMethodWithOriginalName("nonValueStaticField"), "TWO");
    } else {
      assertNameWasNotReplaced(clazz.uniqueMethodWithOriginalName("simple"));
      assertNameWasNotReplaced(clazz.uniqueMethodWithOriginalName("local"));
      assertNameWasNotReplaced(clazz.uniqueMethodWithOriginalName("multipleUsages"));
      assertNameWasNotReplaced(clazz.uniqueMethodWithOriginalName("inlined"));
      assertNameWasNotReplaced(clazz.uniqueMethodWithOriginalName("differentTypeStaticField"));
      assertNameWasNotReplaced(clazz.uniqueMethodWithOriginalName("nonStaticGet"));
    }

    // TODO(jakew) this should be allowed!
    assertNameWasNotReplaced(clazz.uniqueMethodWithOriginalName("libraryType"));

    assertNameWasNotReplaced(clazz.uniqueMethodWithOriginalName("phi"));

    assertThat(clazz.uniqueMethodWithOriginalName("inlined2"), isAbsent());
  }

  @Test
  public void toStrings() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(ToStrings.class)
        .addKeepMainRule(ToStrings.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableSideEffectAnnotations()
        .addOptionsModification(this::configure)
        .addOptionsModification(
            o -> {
              o.testing.enableLir();
              // Not inlining toString depends on simple inlining limit.
              o.inlinerOptions().simpleInliningInstructionLimit = 3;
            })
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspectToStrings)
        .run(parameters.getRuntime(), ToStrings.class)
        .assertSuccessWithOutputLines(
            "one", "one", "TWO", "TWO", "TWO", "1TWO", "TWO", "SECONDS", "DOWN", "TWO", "TWO",
            "TWO");
  }

  private void inspectToStrings(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(ToStrings.class);
    assertTrue(clazz.isPresent());

    assertToStringWasNotReplaced(clazz.uniqueMethodWithOriginalName("typeToString"));
    assertToStringReplacedWithConst(clazz.uniqueMethodWithOriginalName("valueWithToString"), "one");
    assertToStringWasNotReplaced(clazz.uniqueMethodWithOriginalName("valueWithoutToString"));

    if (enableOptimization) {
      assertToStringReplacedWithConst(clazz.uniqueMethodWithOriginalName("noToString"), "TWO");
      assertToStringReplacedWithConst(clazz.uniqueMethodWithOriginalName("local"), "TWO");
      assertToStringReplacedWithConst(clazz.uniqueMethodWithOriginalName("multipleUsages"), "TWO");
      assertToStringReplacedWithConst(clazz.uniqueMethodWithOriginalName("inlined"), "TWO");
      assertToStringReplacedWithConst(
          clazz.uniqueMethodWithOriginalName("nonValueStaticField"), "TWO");
      assertToStringReplacedWithConst(
          clazz.uniqueMethodWithOriginalName("differentTypeStaticField"), "DOWN");
      assertToStringReplacedWithConst(clazz.uniqueMethodWithOriginalName("nonStaticGet"), "TWO");
    } else {
      assertToStringWasNotReplaced(clazz.uniqueMethodWithOriginalName("noToString"));
      assertToStringWasNotReplaced(clazz.uniqueMethodWithOriginalName("local"));
      assertToStringWasNotReplaced(clazz.uniqueMethodWithOriginalName("multipleUsages"));
      assertToStringWasNotReplaced(clazz.uniqueMethodWithOriginalName("inlined"));
      assertToStringWasNotReplaced(clazz.uniqueMethodWithOriginalName("nonValueStaticField"));
      assertToStringWasNotReplaced(clazz.uniqueMethodWithOriginalName("differentTypeStaticField"));
      assertToStringWasNotReplaced(clazz.uniqueMethodWithOriginalName("nonStaticGet"));
    }

    assertToStringWasNotReplaced(clazz.uniqueMethodWithOriginalName("libraryType"));
    assertToStringWasNotReplaced(clazz.uniqueMethodWithOriginalName("phi"));

    assertThat(clazz.uniqueMethodWithOriginalName("inlined2"), isAbsent());
  }

  private static void assertOrdinalReplacedWithConst(MethodSubject method, int expectedConst) {
    assertThat(method, isPresent());
    assertEquals(emptyList(), enumInvokes(method, "ordinal"));

    long[] actualConst = method.streamInstructions()
        .filter(InstructionSubject::isConstNumber)
        .mapToLong(InstructionSubject::getConstNumber)
        .toArray();
    assertEquals(expectedConst, actualConst[0]);
  }

  private static void assertOrdinalReplacedAndGone(MethodSubject method) {
    assertThat(method, isPresent());
    assertEquals(emptyList(), enumInvokes(method, "ordinal"));
    assertTrue(method.streamInstructions().noneMatch(InstructionSubject::isConstNumber));
  }

  private static void assertOrdinalWasNotReplaced(MethodSubject method) {
    assertThat(method, isPresent());
    List<InstructionSubject> invokes = enumInvokes(method, "ordinal");
    assertEquals(invokes.toString(), 1, invokes.size());
  }

  private static List<InstructionSubject> enumInvokes(MethodSubject method, String methodName) {
    return method.streamInstructions()
        .filter(instruction -> instruction.isInvoke()
            && instruction.getMethod().name.toString().equals(methodName))
        .collect(toList());
  }

  private static void assertNameReplacedWithConst(MethodSubject method, String expectedConst) {
    assertThat(method, isPresent());
    assertEquals(emptyList(), enumInvokes(method, "name"));

    List<String> actualConst = method.streamInstructions()
        .map(InstructionSubject::getConstString)
        .filter(Objects::nonNull)
        .collect(toList());
    assertEquals(expectedConst, actualConst.get(0));
  }

  private static void assertNameWasNotReplaced(MethodSubject method) {
    assertThat(method, isPresent());
    List<InstructionSubject> invokes = enumInvokes(method, "name");
    assertEquals(invokes.toString(), 1, invokes.size());
  }

  private static void assertToStringReplacedWithConst(MethodSubject method, String expectedConst) {
    assertThat(method, isPresent());
    assertEquals(emptyList(), enumInvokes(method, "toString"));

    List<String> actualConst = method.streamInstructions()
        .map(InstructionSubject::getConstString)
        .filter(Objects::nonNull)
        .collect(toList());
    assertEquals(expectedConst, actualConst.get(0));
  }

  private static void assertToStringWasNotReplaced(MethodSubject method) {
    assertThat(method, isPresent());
    List<InstructionSubject> invokes = enumInvokes(method, "toString");
    assertEquals(invokes.toString(), 1, invokes.size());
  }
}
