// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.enums;

import static com.android.tools.r8.ToolHelper.getDefaultAndroidJar;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests checking that Enum.ordinal() calls on enum fields are rewritten into a constant integer.
 */
@RunWith(Parameterized.class)
public class EnumOptimizationTest extends TestBase {
  private final boolean enableOptimization;
  private final TestParameters parameters;

  @Parameters(name = "{1}, enable enum optimization: {0}")
  public static List<Object[]> data() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withAllRuntimes().build());
  }

  public EnumOptimizationTest(boolean enableOptimization, TestParameters parameters) {
    this.enableOptimization = enableOptimization;
    this.parameters = parameters;
  }

  static class Ordinals {
    enum Number {
      ONE, TWO;

      public static final Direction DOWN = Direction.DOWN;
      public static final Number DEFAULT = TWO;
    }

    enum Direction {
      UP, DOWN
    }

    @NeverInline
    static long simple() {
      return Number.TWO.ordinal();
    }

    @NeverInline
    static long local() {
      Number two = Number.TWO;
      return two.ordinal();
    }

    @NeverInline
    static String multipleUsages() {
      Number two = Number.TWO;
      return two.name() + two.ordinal();
    }

    @NeverInline
    static long inlined() {
      return inlined2(Number.TWO);
    }
    @ForceInline
    private static long inlined2(Number number) {
      return number.ordinal();
    }

    @NeverInline
    static long libraryType() {
      return TimeUnit.SECONDS.ordinal();
    }

    @NeverInline
    static long wrongTypeStaticField() {
      return Number.DOWN.ordinal();
    }

    @NeverInline
    static long nonValueStaticField() {
      return Number.DEFAULT.ordinal();
    }

    @NeverInline
    static long phi(boolean value) {
      Number number = Number.ONE;
      if (value) {
        number = Number.TWO;
      }
      return number.ordinal();
    }

    @NeverInline
    static long nonStaticGet() {
      return new Ordinals().two.ordinal();
    }
    private final Number two = Number.TWO;

    public static void main(String[] args) {
      System.out.println(simple());
      System.out.println(local());
      System.out.println(multipleUsages());
      System.out.println(inlined());
      System.out.println(libraryType());
      System.out.println(wrongTypeStaticField());
      System.out.println(nonValueStaticField());
      System.out.println(phi(true));
      System.out.println(nonStaticGet());
    }
  }

  @Test public void ordinals() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(getDefaultAndroidJar())
        .addProgramClassesAndInnerClasses(Ordinals.class)
        .addKeepMainRule(Ordinals.class)
        .noMinification()
        .enableInliningAnnotations()
        .addOptionsModification(options -> options.enableEnumValueOptimization = enableOptimization)
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(this::inspectOrdinals)
        .run(parameters.getRuntime(), Ordinals.class)
        .assertSuccessWithOutputLines("1", "1", "TWO1", "1", "3", "1", "1", "1", "1");
  }

  private void inspectOrdinals(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Ordinals.class);
    assertTrue(clazz.isPresent());

    if (enableOptimization) {
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithName("simple"), 1);
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithName("local"), 1);
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithName("multipleUsages"), 1);
      assertOrdinalReplacedWithConst(clazz.uniqueMethodWithName("inlined"), 1);
    } else {
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("simple"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("local"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("multipleUsages"));
      assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("inlined"));
    }

    assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("libraryType"));
    assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("wrongTypeStaticField"));
    assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("nonValueStaticField"));
    assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("phi"));
    assertOrdinalWasNotReplaced(clazz.uniqueMethodWithName("nonStaticGet"));
  }

  private static void assertOrdinalReplacedWithConst(MethodSubject method, int expectedConst) {
    assertTrue(method.isPresent());
    assertEquals(emptyList(), enumOrdinalInvokes(method));

    long[] actualConst = method.streamInstructions()
        .filter(InstructionSubject::isConstNumber)
        .mapToLong(InstructionSubject::getConstNumber)
        .toArray();
    assertEquals(expectedConst, actualConst[0]);
  }

  private static void assertOrdinalWasNotReplaced(MethodSubject method) {
    assertTrue(method.isPresent());
    List<InstructionSubject> invokes = enumOrdinalInvokes(method);
    assertEquals(invokes.toString(), 1, invokes.size());
  }

  private static List<InstructionSubject> enumOrdinalInvokes(MethodSubject method) {
    return method.streamInstructions()
        .filter(instruction -> instruction.isInvoke()
            && instruction.getMethod().name.toString().equals("ordinal"))
        .collect(toList());
  }
}
