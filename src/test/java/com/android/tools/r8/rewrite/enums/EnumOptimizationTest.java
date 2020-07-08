// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.enums;

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
import org.junit.Assume;
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
        .enableInliningAnnotations()
        .enableSideEffectAnnotations()
        .addOptionsModification(this::configure)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspectOrdinals)
        .run(parameters.getRuntime(), Ordinals.class)
        .assertSuccessWithOutputLines("1", "1", "TWO1", "1", "11", "3", "1", "1", "1", "1");
  }

  private void inspectOrdinals(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Ordinals.class);
    assertTrue(clazz.isPresent());

    if (enableOptimization) {
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithName("simple"), 1);
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithName("local"), 1);
      // TODO(b/160667929): Re-enable name()/toString() optimizations.
      // String concatenation optimization is enabled for DEX output.
      // Even replaced ordinal is concatenated (and gone).
      //      if (parameters.isDexRuntime()) {
      //        assertOrdinalReplacedAndGone(clazz.uniqueMethodWithName("multipleUsages"));
      //      } else {
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithName("multipleUsages"), 1);
      //      }
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithName("inlined"), 1);
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithName("inSwitch"), 11);
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithName("differentTypeStaticField"), 1);
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithName("nonStaticGet"), 1);
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithName("nonValueStaticField"), 1);
    } else {
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("simple"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("local"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("multipleUsages"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("inlined"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("inSwitch"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("differentTypeStaticField"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("nonStaticGet"));
    }

    assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("libraryType"));
    assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("phi"));
  }

  @Test
  public void names() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(Names.class)
        .addKeepMainRule(Names.class)
        .enableInliningAnnotations()
        .enableSideEffectAnnotations()
        .addOptionsModification(this::configure)
        .setMinApi(parameters.getApiLevel())
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
      Assume.assumeTrue("TODO(b/160667929): Re-enable name()/toString() optimizations.", false);
      assertNameReplacedWithConst(clazz.uniqueMethodWithName("simple"), "TWO");
      assertNameReplacedWithConst(clazz.uniqueMethodWithName("local"), "TWO");
      // String concatenation optimization is enabled for DEX output.
      String expectedConst = parameters.isDexRuntime() ? "1TWO" : "TWO";
      assertNameReplacedWithConst(clazz.uniqueMethodWithName("multipleUsages"), expectedConst);
      assertNameReplacedWithConst(clazz.uniqueMethodWithName("inlined"), "TWO");
      assertNameReplacedWithConst(clazz.uniqueMethodWithName("differentTypeStaticField"), "DOWN");
      assertNameReplacedWithConst(clazz.uniqueMethodWithName("nonStaticGet"), "TWO");
      assertNameReplacedWithConst(clazz.uniqueMethodWithName("nonValueStaticField"), "TWO");
    } else {
      assertNameWasNotReplaced(clazz.uniqueMethodWithName("simple"));
      assertNameWasNotReplaced(clazz.uniqueMethodWithName("local"));
      assertNameWasNotReplaced(clazz.uniqueMethodWithName("multipleUsages"));
      assertNameWasNotReplaced(clazz.uniqueMethodWithName("inlined"));
      assertNameWasNotReplaced(clazz.uniqueMethodWithName("differentTypeStaticField"));
      assertNameWasNotReplaced(clazz.uniqueMethodWithName("nonStaticGet"));
    }

    // TODO(jakew) this should be allowed!
    assertNameWasNotReplaced(clazz.uniqueMethodWithName("libraryType"));

    assertNameWasNotReplaced(clazz.uniqueMethodWithName("phi"));
  }

  @Test
  public void toStrings() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(ToStrings.class)
        .addKeepMainRule(ToStrings.class)
        .enableInliningAnnotations()
        .enableSideEffectAnnotations()
        .addOptionsModification(this::configure)
        .setMinApi(parameters.getApiLevel())
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

    assertToStringWasNotReplaced(clazz.uniqueMethodWithName("typeToString"));
    assertToStringReplacedWithConst(clazz.uniqueMethodWithName("valueWithToString"), "one");
    assertToStringWasNotReplaced(clazz.uniqueMethodWithName("valueWithoutToString"));

    if (enableOptimization) {
      Assume.assumeTrue("TODO(b/160667929): Re-enable name()/toString() optimizations.", false);
      assertToStringReplacedWithConst(clazz.uniqueMethodWithName("noToString"), "TWO");
      assertToStringReplacedWithConst(clazz.uniqueMethodWithName("local"), "TWO");
      assertToStringReplacedWithConst(clazz.uniqueMethodWithName("multipleUsages"), "TWO");
      assertToStringReplacedWithConst(clazz.uniqueMethodWithName("inlined"), "TWO");
      assertToStringReplacedWithConst(clazz.uniqueMethodWithName("nonValueStaticField"), "TWO");
      assertToStringReplacedWithConst(
          clazz.uniqueMethodWithName("differentTypeStaticField"), "DOWN");
      assertToStringReplacedWithConst(clazz.uniqueMethodWithName("nonStaticGet"), "TWO");
    } else {
      assertToStringWasNotReplaced(clazz.uniqueMethodWithName("noToString"));
      assertToStringWasNotReplaced(clazz.uniqueMethodWithName("local"));
      assertToStringWasNotReplaced(clazz.uniqueMethodWithName("multipleUsages"));
      assertToStringWasNotReplaced(clazz.uniqueMethodWithName("inlined"));
      assertToStringWasNotReplaced(clazz.uniqueMethodWithName("nonValueStaticField"));
      assertToStringWasNotReplaced(clazz.uniqueMethodWithName("differentTypeStaticField"));
      assertToStringWasNotReplaced(clazz.uniqueMethodWithName("nonStaticGet"));
    }

    assertToStringWasNotReplaced(clazz.uniqueMethodWithName("libraryType"));
    assertToStringWasNotReplaced(clazz.uniqueMethodWithName("phi"));
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
