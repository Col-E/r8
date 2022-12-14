// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepOption;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepAnnotationViaSuperTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("42");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public KeepAnnotationViaSuperTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getInputClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithRuleExtraction() throws Exception {
    List<String> rules = getExtractedKeepRules();
    System.out.println(rules);
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getInputClassesWithoutKeepAnnotations())
        .addKeepRules(rules)
        .addKeepMainRule(TestClass.class)
        .addKeepRuntimeVisibleAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkOutput);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(
        TestClass.class,
        Base.class,
        SubA.class,
        SubB.class,
        SubC.class,
        Anno.class,
        UnusedAnno.class);
  }

  public List<byte[]> getInputClassesWithoutKeepAnnotations() throws Exception {
    return KeepEdgeAnnotationsTest.getInputClassesWithoutKeepAnnotations(getInputClasses());
  }

  public List<String> getExtractedKeepRules() throws Exception {
    List<Class<?>> classes = getInputClasses();
    List<String> rules = new ArrayList<>();
    for (Class<?> clazz : classes) {
      rules.addAll(KeepEdgeAnnotationsTest.getKeepRulesForClass(clazz));
    }
    return rules;
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(Base.class), isPresent());

    ClassSubject classA = inspector.clazz(SubA.class);
    assertThat(classA, isPresent());
    assertThat(classA.annotation(Anno.class), isPresent());
    assertThat(classA.annotation(UnusedAnno.class), isAbsent());

    ClassSubject classB = inspector.clazz(SubB.class);
    assertThat(classB, isPresent());
    assertThat(classB.annotation(Anno.class), isPresent());

    assertThat(inspector.clazz(SubC.class), isAbsent());
  }

  @Target({ElementType.TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @interface Anno {
    int value();
  }

  @Target({ElementType.TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @interface UnusedAnno {
    int value();
  }

  abstract static class Base {

    @UsesReflection({
      @KeepTarget(
          extendsClassConstant = Base.class,
          disallow = {KeepOption.ANNOTATION_REMOVAL})
    })
    public Base() {
      Anno annotation = getClass().getAnnotation(Anno.class);
      System.out.println(annotation.value());
    }
  }

  @Anno(42)
  @UnusedAnno(123)
  static class SubA extends Base {}

  @Anno(7)
  static class SubB extends Base {}

  // Unused.
  @Anno(-1)
  static class SubC extends Base {}

  static class TestClass {

    public static void main(String[] args) throws Exception {
      Base b = System.nanoTime() > 0 ? new SubA() : new SubB();
    }
  }
}
