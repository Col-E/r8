// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
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
public class VerticalClassMergingAcrossFeatureSplitTest extends SplitterTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public VerticalClassMergingAcrossFeatureSplitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(BaseClass.class)
            // Link against android.jar that contains ReflectiveOperationException.
            .addLibraryFiles(parameters.getDefaultAndroidJarAbove(AndroidApiLevel.K))
            .addFeatureSplitRuntime()
            .addFeatureSplit(Feature1Class.class)
            .addFeatureSplit(Feature2Main.class, Feature2Class.class)
            .addKeepFeatureMainRule(Feature2Main.class)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .setMinApi(parameters)
            .compile()
            .inspect(this::inspectBase, this::inspectFeature1, this::inspectFeature2);

    // Run feature 2 on top of feature 1.
    compileResult
        .runFeature(
            parameters.getRuntime(),
            Feature2Main.class,
            compileResult.getFeature(1),
            compileResult.getFeature(0))
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspectBase(CodeInspector inspector) {
    assertThat(inspector.clazz(BaseClass.class), isPresent());
  }

  private void inspectFeature1(CodeInspector inspector) {
    assertThat(inspector.clazz(Feature1Class.class), isPresent());
  }

  private void inspectFeature2(CodeInspector inspector) {
    assertThat(inspector.clazz(Feature2Class.class), isPresent());
  }

  // Base.

  public static class BaseClass {

    @NeverInline
    public void greet() {
      System.out.println("world!");
    }
  }

  // Feature 1.

  public static class Feature1Class extends BaseClass {

    @NeverInline
    @Override
    public void greet() {
      System.out.print(" ");
      super.greet();
    }
  }

  // Feature 2.

  public static class Feature2Main implements RunInterface {

    @Override
    public void run() {
      new Feature2Class().greet();
    }
  }

  @NeverClassInline
  static class Feature2Class extends Feature1Class {

    @NeverInline
    @Override
    public void greet() {
      System.out.print("Hello");
      super.greet();
    }
  }
}
