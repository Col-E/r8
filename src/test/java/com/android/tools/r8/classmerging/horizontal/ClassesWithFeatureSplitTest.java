// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dexsplitter.SplitterTestBase.RunInterface;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runners.Parameterized;

public class ClassesWithFeatureSplitTest extends HorizontalClassMergingTestBase {
  public ClassesWithFeatureSplitTest(TestParameters parameters) {
    super(parameters);
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(Base.class)
            // Link against android.jar that contains ReflectiveOperationException.
            .addLibraryFiles(parameters.getDefaultAndroidJarAbove(AndroidApiLevel.K))
            .addFeatureSplitRuntime()
            .addFeatureSplit(Feature1Class1.class, Feature1Class2.class, Feature1Main.class)
            .addFeatureSplit(Feature2Class.class, Feature2Main.class)
            .addKeepFeatureMainRule(Feature1Main.class)
            .addKeepFeatureMainRule(Feature2Main.class)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .setMinApi(parameters)
            .compile()
            .inspect(this::inspectBase, this::inspectFeature1, this::inspectFeature2);

    compileResult
        .runFeature(parameters.getRuntime(), Feature1Main.class, compileResult.getFeature(0))
        .assertSuccessWithOutputLines("base", "feature 1 class 1", "feature 1 class 2");

    compileResult
        .runFeature(parameters.getRuntime(), Feature2Main.class, compileResult.getFeature(1))
        .assertSuccessWithOutputLines("base", "feature 2");
  }

  private void inspectBase(CodeInspector inspector) {
    assertThat(inspector.clazz(Base.class), isPresent());
    assertThat(inspector.clazz(Feature1Class1.class), not(isPresent()));
    assertThat(inspector.clazz(Feature1Class2.class), not(isPresent()));
    assertThat(inspector.clazz(Feature2Class.class), not(isPresent()));
  }

  private void inspectFeature1(CodeInspector inspector) {
    assertThat(inspector.clazz(Feature1Main.class), isPresent());
    assertThat(inspector.clazz(Feature1Class1.class), isPresent());
    assertThat(inspector.clazz(Feature1Class2.class), not(isPresent()));
    assertThat(inspector.clazz(Feature2Main.class), not(isPresent()));
    assertThat(inspector.clazz(Feature2Class.class), not(isPresent()));
  }

  private void inspectFeature2(CodeInspector inspector) {
    assertThat(inspector.clazz(Feature1Main.class), not(isPresent()));
    assertThat(inspector.clazz(Feature1Class1.class), not(isPresent()));
    assertThat(inspector.clazz(Feature1Class2.class), not(isPresent()));
    assertThat(inspector.clazz(Feature2Main.class), isPresent());
    assertThat(inspector.clazz(Feature2Class.class), isPresent());
  }

  @NeverClassInline
  public static class Base {
    public Base() {
      System.out.println("base");
    }
  }

  @NeverClassInline
  public static class Feature1Class1 {
    public Feature1Class1() {
      System.out.println("feature 1 class 1");
    }
  }

  @NeverClassInline
  public static class Feature1Class2 {
    public Feature1Class2() {
      System.out.println("feature 1 class 2");
    }
  }

  @NeverClassInline
  public static class Feature2Class {
    @NeverInline
    public Feature2Class() {
      System.out.println("feature 2");
    }
  }

  public static class Feature1Main implements RunInterface {

    @Override
    public void run() {
      new Base();
      new Feature1Class1();
      new Feature1Class2();
    }
  }

  public static class Feature2Main implements RunInterface {

    @Override
    public void run() {
      new Base();
      new Feature2Class();
    }
  }
}
