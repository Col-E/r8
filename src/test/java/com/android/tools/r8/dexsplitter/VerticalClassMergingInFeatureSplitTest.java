// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class VerticalClassMergingInFeatureSplitTest extends SplitterTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public VerticalClassMergingInFeatureSplitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(BaseClass.class, BaseClassWithBaseSubclass.class)
            .addFeatureSplitRuntime()
            .addFeatureSplit(
                Feature1Main.class, Feature1Class.class, Feature1ClassWithSameFeatureSubclass.class)
            .addFeatureSplit(
                Feature2Main.class, Feature2Class.class, Feature2ClassWithSameFeatureSubclass.class)
            .addKeepFeatureMainRules(Feature1Main.class, Feature2Main.class)
            // Link against android.jar that contains ReflectiveOperationException.
            .addLibraryFiles(parameters.getDefaultAndroidJarAbove(AndroidApiLevel.K))
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .setMinApi(parameters)
            .compile()
            .inspect(this::inspectBase, this::inspectFeature1, this::inspectFeature2);

    compileResult
        .runFeature(parameters.getRuntime(), Feature1Main.class, compileResult.getFeature(0))
        .assertSuccessWithOutputLines("Hello world!");

    compileResult
        .runFeature(parameters.getRuntime(), Feature2Main.class, compileResult.getFeature(1))
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspectBase(CodeInspector inspector) {
    assertThat(inspector.clazz(BaseClass.class), isPresent());
    assertThat(inspector.clazz(BaseClassWithBaseSubclass.class), not(isPresent()));
  }

  private void inspectFeature1(CodeInspector inspector) {
    assertThat(inspector.clazz(Feature1Class.class), isPresent());
    assertThat(inspector.clazz(Feature1ClassWithSameFeatureSubclass.class), not(isPresent()));
  }

  private void inspectFeature2(CodeInspector inspector) {
    assertThat(inspector.clazz(Feature2Class.class), isPresent());
    assertThat(inspector.clazz(Feature2ClassWithSameFeatureSubclass.class), not(isPresent()));
  }

  // Base.

  static class BaseClassWithBaseSubclass {

    @NeverInline
    public void greet() {
      System.out.print(" ");
    }
  }

  @NeverClassInline
  public static class BaseClass extends BaseClassWithBaseSubclass {

    @NeverInline
    @Override
    public void greet() {
      System.out.print("Hello");
      super.greet();
    }
  }

  // Feature 1.

  public static class Feature1Main implements RunInterface {

    @Override
    public void run() {
      new BaseClass().greet();
      new Feature1Class().greet();
    }
  }

  static class Feature1ClassWithSameFeatureSubclass {

    @NeverInline
    public void greet() {
      System.out.println("!");
    }
  }

  @NeverClassInline
  static class Feature1Class extends Feature1ClassWithSameFeatureSubclass {

    @NeverInline
    @Override
    public void greet() {
      System.out.print("world");
      super.greet();
    }
  }

  // Feature 2.

  public static class Feature2Main implements RunInterface {

    @NeverInline
    @Override
    public void run() {
      new BaseClass().greet();
      new Feature2Class().greet();
    }
  }

  static class Feature2ClassWithSameFeatureSubclass {

    @NeverInline
    public void greet() {
      System.out.println("!");
    }
  }

  @NeverClassInline
  static class Feature2Class extends Feature2ClassWithSameFeatureSubclass {

    @NeverInline
    @Override
    public void greet() {
      System.out.print("world");
      super.greet();
    }
  }
}
