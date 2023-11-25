// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing.enummerging;

import static com.android.tools.r8.ToolHelper.getClassFilesForInnerClasses;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AbstractMethodErrorEnumMergingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;
  private final String EXPECTED_RESULT =
      StringUtils.lines(
          "class java.lang.AbstractMethodError",
          "74",
          "class java.lang.AbstractMethodError",
          "44",
          "class java.lang.AbstractMethodError",
          "class java.lang.AbstractMethodError");

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public AbstractMethodErrorEnumMergingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testReference() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(inputProgram())
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(inputProgram())
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(
            inspector -> inspector.assertUnboxed(MyEnum2Cases.class, MyEnum1Case.class))
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private List<byte[]> inputProgram() throws Exception {
    Collection<Path> files = getClassFilesForInnerClasses(getClass());
    List<byte[]> result = new ArrayList<>();
    int changed = 0;
    for (Path file : files) {
      String fileName = file.getFileName().toString();
      if (fileName.equals("AbstractMethodErrorEnumMergingTest$MyEnum1Case$1.class")
          || fileName.equals("AbstractMethodErrorEnumMergingTest$MyEnum2Cases$1.class")) {
        result.add(transformer(file, null).removeMethodsWithName("operate").transform());
        changed++;
      } else {
        result.add(Files.readAllBytes(file));
      }
    }
    assertEquals(2, changed);
    return result;
  }

  enum MyEnum2Cases {
    A(8) {
      // Will be removed by transformation before compilation.
      @NeverInline
      @Override
      public long operate(long another) {
        throw new RuntimeException("Should have been removed");
      }
    },
    B(32) {
      @NeverInline
      @Override
      public long operate(long another) {
        return num + another;
      }
    };
    final long num;

    MyEnum2Cases(long num) {
      this.num = num;
    }

    public abstract long operate(long another);
  }

  enum MyEnum1Case {
    A(8) {
      // Will be removed by transformation before compilation.
      @NeverInline
      @Override
      public long operate(long another) {
        throw new RuntimeException("Should have been removed");
      }
    };
    final long num;

    MyEnum1Case(long num) {
      this.num = num;
    }

    public abstract long operate(long another);
  }

  static class Main {

    public static void main(String[] args) {
      try {
        System.out.println(MyEnum2Cases.A.operate(42));
      } catch (Throwable t) {
        System.out.println(t.getClass());
      }
      System.out.println(MyEnum2Cases.B.operate(42));
      try {
        System.out.println(indirect(MyEnum2Cases.A));
      } catch (Throwable t) {
        System.out.println(t.getClass());
      }
      System.out.println(indirect(MyEnum2Cases.B));

      try {
        System.out.println(MyEnum1Case.A.operate(42));
      } catch (Throwable t) {
        System.out.println(t.getClass());
      }
      try {
        System.out.println(indirect(MyEnum1Case.A));
      } catch (Throwable t) {
        System.out.println(t.getClass());
      }
    }

    @NeverInline
    public static long indirect(MyEnum2Cases e) {
      return e.operate(12);
    }

    @NeverInline
    public static long indirect(MyEnum1Case e) {
      return e.operate(7);
    }
  }
}
