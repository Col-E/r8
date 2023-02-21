// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceEnumUnboxingTest extends EnumUnboxingTestBase {

  private static final Class<?>[] TESTS = {
    FailureDefaultMethodUsed.class,
    FailureUsedAsInterface.class,
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
            .addKeepMainRules(TESTS)
            .addEnumUnboxingInspector(
                inspector ->
                    inspector
                        .assertUnboxed(
                            SuccessAbstractMethod.EnumInterface.class,
                            SuccessEmptyInterface.EnumInterface.class,
                            SuccessUnusedDefaultMethod.EnumInterface.class,
                            SuccessUnusedDefaultMethodOverride.EnumInterface.class,
                            SuccessUnusedDefaultMethodOverrideEnum.EnumInterface.class)
                        .assertNotUnboxed(FailureUsedAsInterface.EnumInterface.class)
                        // When desugaring interfaces the dispatch will inline the forwarding method
                        // to the CC method allowing unboxing.
                        .assertUnboxedIf(
                            !parameters.canUseDefaultAndStaticInterfaceMethods(),
                            FailureDefaultMethodUsed.EnumInterface.class))
            .addDontObfuscate()
            .enableNoVerticalClassMergingAnnotations()
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .addKeepRules(enumKeepRules.getKeepRules())
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .setMinApi(parameters)
            .compile();
    for (Class<?> main : TESTS) {
      testClass(compile, main);
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
