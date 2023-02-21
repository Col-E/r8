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
public class StaticClassMergingInFeatureSplitTest extends SplitterTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public StaticClassMergingInFeatureSplitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(BaseClassA.class, BaseClassB.class)
            // Link against android.jar that contains ReflectiveOperationException.
            .addLibraryFiles(parameters.getDefaultAndroidJarAbove(AndroidApiLevel.K))
            .addFeatureSplitRuntime()
            .addFeatureSplit(Feature1Main.class, Feature1ClassA.class, Feature1ClassB.class)
            .addFeatureSplit(Feature2Main.class, Feature2ClassA.class, Feature2ClassB.class)
            .addKeepFeatureMainRules(Feature1Main.class, Feature2Main.class)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .setMinApi(parameters)
            .compile()
            .inspect(this::inspectBase, this::inspectFeature1, this::inspectFeature2);

    compileResult
        .runFeature(parameters.getRuntime(), Feature1Main.class, compileResult.getFeature(0))
        .assertSuccessWithOutputLines("Hello from feature 1!");

    compileResult
        .runFeature(parameters.getRuntime(), Feature2Main.class, compileResult.getFeature(1))
        .assertSuccessWithOutputLines("Hello from feature 2!");
  }

  private void inspectBase(CodeInspector inspector) {
    assertThat(inspector.clazz(BaseClassA.class), isPresent());
    assertThat(inspector.clazz(BaseClassB.class), not(isPresent()));
  }

  private void inspectFeature1(CodeInspector inspector) {
    assertThat(inspector.clazz(Feature1ClassA.class), isPresent());
    assertThat(inspector.clazz(Feature1ClassB.class), not(isPresent()));
  }

  private void inspectFeature2(CodeInspector inspector) {
    assertThat(inspector.clazz(Feature2ClassA.class), isPresent());
    assertThat(inspector.clazz(Feature2ClassB.class), not(isPresent()));
  }

  // Base.

  public static class BaseClassA {

    @NeverInline
    public static void greet() {
      System.out.print("Hello");
    }
  }

  @NeverClassInline
  public static class BaseClassB {

    @NeverInline
    public static void greet() {
      System.out.print(" ");
    }
  }

  // Feature 1.

  public static class Feature1Main implements RunInterface {

    public void run() {
      BaseClassA.greet();
      BaseClassB.greet();
      Feature1ClassA.greet();
      Feature1ClassB.greet();
    }
  }

  static class Feature1ClassA {

    @NeverInline
    public static void greet() {
      System.out.print("from feature 1");
    }
  }

  @NeverClassInline
  static class Feature1ClassB {

    @NeverInline
    public static void greet() {
      System.out.println("!");
    }
  }

  // Feature 2.

  public static class Feature2Main implements RunInterface {

    @NeverInline
    public void run() {
      BaseClassA.greet();
      BaseClassB.greet();
      Feature2ClassA.greet();
      Feature2ClassB.greet();
    }
  }

  static class Feature2ClassA {

    @NeverInline
    public static void greet() {
      System.out.print("from feature 2");
    }
  }

  @NeverClassInline
  static class Feature2ClassB {

    @NeverInline
    public static void greet() {
      System.out.println("!");
    }
  }
}
