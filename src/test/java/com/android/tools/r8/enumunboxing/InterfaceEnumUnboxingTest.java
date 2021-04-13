// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceEnumUnboxingTest extends EnumUnboxingTestBase {

  private static final Class<?>[] FAILURES = {
    FailureDefaultMethodUsed.class, FailureUsedAsInterface.class,
  };

  private static final Class<?>[] SUCCESSES = {
    SuccessAbstractMethod.class,
    SuccessEmptyInterface.class,
    SuccessUnusedDefaultMethod.class,
    SuccessUnusedDefaultMethodOverride.class,
    SuccessUnusedDefaultMethodOverrideEnum.class
  };

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public InterfaceEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxingFailure() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addInnerClasses(InterfaceEnumUnboxingTest.class)
            .addKeepMainRules(SUCCESSES)
            .addKeepMainRules(FAILURES)
            .addEnumUnboxingInspector(
                inspector -> {
                  Arrays.stream(SUCCESSES)
                      .flatMap(
                          clazz -> Arrays.stream(clazz.getDeclaredClasses()).filter(Class::isEnum))
                      .forEach(clazz -> inspector.assertUnboxed((Class<? extends Enum<?>>) clazz));
                  Arrays.stream(FAILURES)
                      .flatMap(
                          clazz -> Arrays.stream(clazz.getDeclaredClasses()).filter(Class::isEnum))
                      .forEach(
                          clazz -> inspector.assertNotUnboxed((Class<? extends Enum<?>>) clazz));
                })
            .noMinification()
            .enableNoVerticalClassMergingAnnotations()
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .addKeepRules(enumKeepRules.getKeepRules())
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .setMinApi(parameters.getApiLevel())
            .compile();
    for (Class<?> failure : FAILURES) {
      testClass(compile, failure);
    }
    for (Class<?> success : SUCCESSES) {
      testClass(compile, success);
    }
  }

  private void testClass(R8TestCompileResult compile, Class<?> testClass) throws Exception {
    compile
        .run(parameters.getRuntime(), testClass)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  static class SuccessEmptyInterface {

    public static void main(String[] args) {
      System.out.println(EnumInterface.A.ordinal());
      System.out.println(0);
    }

    @NeverClassInline
    enum EnumInterface implements Itf {
      A,
      B,
      C
    }

    @NoVerticalClassMerging
    interface Itf {}
  }

  static class SuccessUnusedDefaultMethodOverrideEnum {

    public static void main(String[] args) {
      System.out.println(EnumInterface.A.ordinal());
      System.out.println(0);
    }

    @NeverClassInline
    enum EnumInterface implements Itf {
      A,
      B,
      C
    }

    @NoVerticalClassMerging
    interface Itf {
      @NeverInline
      default int ordinal() {
        return System.currentTimeMillis() > 0 ? 3 : -3;
      }
    }
  }

  static class SuccessUnusedDefaultMethodOverride {

    public static void main(String[] args) {
      System.out.println(EnumInterface.A.method());
      System.out.println(5);
    }

    @NeverClassInline
    enum EnumInterface implements Itf {
      A,
      B,
      C;

      @Override
      @NeverInline
      public int method() {
        return System.currentTimeMillis() > 0 ? 5 : -5;
      }
    }

    @NoVerticalClassMerging
    interface Itf {
      @NeverInline
      default int method() {
        return System.currentTimeMillis() > 0 ? 3 : -3;
      }
    }
  }

  static class SuccessUnusedDefaultMethod {

    public static void main(String[] args) {
      System.out.println(EnumInterface.A.ordinal());
      System.out.println(0);
    }

    @NeverClassInline
    enum EnumInterface implements Itf {
      A,
      B,
      C
    }

    @NoVerticalClassMerging
    interface Itf {
      @NeverInline
      default int method() {
        return System.currentTimeMillis() > 0 ? 3 : -3;
      }
    }
  }

  static class SuccessAbstractMethod {

    public static void main(String[] args) {
      System.out.println(EnumInterface.A.method());
      System.out.println(5);
    }

    @NeverClassInline
    enum EnumInterface implements Itf {
      A,
      B,
      C;

      @Override
      @NeverInline
      public int method() {
        return System.currentTimeMillis() > 0 ? 5 : -5;
      }
    }

    @NoVerticalClassMerging
    interface Itf {
      int method();
    }
  }

  static class FailureDefaultMethodUsed {

    public static void main(String[] args) {
      System.out.println(EnumInterface.A.method());
      System.out.println(3);
    }

    @NeverClassInline
    enum EnumInterface implements Itf {
      A,
      B,
      C
    }

    @NoVerticalClassMerging
    interface Itf {
      @NeverInline
      default int method() {
        return System.currentTimeMillis() > 0 ? 3 : -3;
      }
    }
  }

  static class FailureUsedAsInterface {

    public static void main(String[] args) {
      print(EnumInterface.A);
      System.out.println(5);
    }

    @NeverInline
    public static void print(Itf itf) {
      System.out.println(itf.method());
    }

    @NeverClassInline
    enum EnumInterface implements Itf {
      A,
      B,
      C;

      @Override
      @NeverInline
      public int method() {
        return System.currentTimeMillis() > 0 ? 5 : -5;
      }
    }

    @NoVerticalClassMerging
    interface Itf {
      @NeverInline
      default int method() {
        return System.currentTimeMillis() > 0 ? 3 : -3;
      }
    }
  }
}
