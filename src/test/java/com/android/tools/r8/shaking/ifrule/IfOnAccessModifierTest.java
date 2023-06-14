// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPackagePrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfOnAccessModifierTest extends ProguardCompatibilityTestBase {

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(ClassForIf.class, ClassForSubsequent.class, MainForAccessModifierTest.class);

  private final TestParameters parameters;
  private final Shrinker shrinker;

  public IfOnAccessModifierTest(TestParameters parameters, Shrinker shrinker) {
    this.parameters = parameters;
    this.shrinker = shrinker;
  }

  @Parameters(name = "{0}, shrinker: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        ImmutableList.of(Shrinker.PROGUARD6, Shrinker.R8));
  }

  private TestShrinkerBuilder<?, ?, ?, ?, ?> getTestBuilder() {
    return getTestBuilder(false);
  }

  private TestShrinkerBuilder<?, ?, ?, ?, ?> getTestBuilder(boolean allowDiagnosticInfoMessages) {
    switch (shrinker) {
      case PROGUARD6:
        assertTrue(parameters.isCfRuntime());
        return testForProguard().addInliningAnnotations().addNeverClassInliningAnnotations();
      case R8:
        return testForR8(parameters.getBackend())
            .allowUnusedProguardConfigurationRules(allowDiagnosticInfoMessages)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations();
      default:
        throw new Unreachable();
    }
  }

  @Test
  public void ifOnPublic_noPublicClassForIfRule() throws Exception {
    assumeFalse(shrinker.isProguard() && parameters.isDexRuntime());

    getTestBuilder(shrinker.isR8())
        .addProgramClasses(CLASSES)
        .addKeepRules(
            "-repackageclasses 'top'",
            "-keep class **.Main* {",
            "  public static void callIfNonPublic();",
            "}",
            "-if public class **.ClassForIf {",
            "  <methods>;",
            "}",
            "-keep,allowobfuscation class **.ClassForSubsequent {",
            "  public <methods>;",
            "}")
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(ClassForIf.class);
              assertThat(classSubject, isPresent());
              MethodSubject methodSubject =
                  classSubject.uniqueMethodWithOriginalName("publicMethod");
              assertThat(methodSubject, not(isPresent()));
              methodSubject = classSubject.uniqueMethodWithOriginalName("nonPublicMethod");
              assertThat(methodSubject, isPresent());
              assertFalse(methodSubject.getMethod().accessFlags.isPublic());
              classSubject = inspector.clazz(ClassForSubsequent.class);
              assertThat(classSubject, not(isPresent()));
            });
  }

  @Test
  public void ifOnNonPublic_keepOnPublic() throws Exception {
    assumeFalse(shrinker.isProguard() && parameters.isDexRuntime());

    getTestBuilder()
        .addProgramClasses(CLASSES)
        .addKeepRules(
            "-repackageclasses 'top'",
            "-allowaccessmodification",
            "-keep class **.Main* {",
            "  public static void callIfNonPublic();",
            "}",
            "-if class **.ClassForIf {",
            "  !public <methods>;",
            "}",
            "-keep,allowobfuscation class **.ClassForSubsequent {",
            "  public <methods>;",
            "}")
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(ClassForIf.class);
              assertThat(classSubject, isPresent());
              MethodSubject methodSubject =
                  classSubject.uniqueMethodWithOriginalName("publicMethod");
              assertThat(methodSubject, not(isPresent()));
              methodSubject = classSubject.uniqueMethodWithOriginalName("nonPublicMethod");
              assertThat(methodSubject, isPresent());
              assertTrue(methodSubject.getMethod().accessFlags.isPublic());

              classSubject = inspector.clazz(ClassForSubsequent.class);
              assertThat(classSubject, isPresent());
              methodSubject = classSubject.uniqueMethodWithOriginalName("publicMethod");
              assertThat(methodSubject, isPresent());
              methodSubject = classSubject.uniqueMethodWithOriginalName("nonPublicMethod");
              assertThat(methodSubject, not(isPresent()));
            });
  }

  @Test
  public void ifOnNonPublic_keepOnNonPublic() throws Exception {
    assumeFalse(shrinker.isProguard() && parameters.isDexRuntime());

    getTestBuilder()
        .addProgramClasses(CLASSES)
        .addKeepRules(
            "-repackageclasses 'top'",
            "-allowaccessmodification",
            "-keep class **.Main* {",
            "  public static void callIfNonPublic();",
            "}",
            "-if class **.ClassForIf {",
            "  !public <methods>;",
            "}",
            "-keep,allowobfuscation class **.ClassForSubsequent {",
            "  !public <methods>;",
            "}")
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(ClassForIf.class);
              assertThat(classSubject, isPresent());
              MethodSubject methodSubject =
                  classSubject.uniqueMethodWithOriginalName("publicMethod");
              assertThat(methodSubject, not(isPresent()));
              methodSubject = classSubject.uniqueMethodWithOriginalName("nonPublicMethod");
              assertThat(methodSubject, isPresent());
              assertTrue(methodSubject.getMethod().accessFlags.isPublic());

              classSubject = inspector.clazz(ClassForSubsequent.class);
              assertThat(classSubject, isPresent());
              methodSubject = classSubject.uniqueMethodWithOriginalName("publicMethod");
              assertThat(methodSubject, not(isPresent()));
              methodSubject = classSubject.uniqueMethodWithOriginalName("nonPublicMethod");
              assertThat(methodSubject, isPresent());
              assertThat(methodSubject, isPackagePrivate());
            });
  }

  @Test
  public void ifOnPublic_keepOnPublic() throws Exception {
    assumeFalse(shrinker.isProguard() && parameters.isDexRuntime());

    getTestBuilder()
        .addProgramClasses(CLASSES)
        .addKeepRules(
            "-repackageclasses 'top'",
            "-allowaccessmodification",
            "-keep class **.Main* {",
            "  public static void callIfPublic();",
            "}",
            "-if class **.ClassForIf {",
            "  public <methods>;",
            "}",
            "-keep,allowobfuscation class **.ClassForSubsequent {",
            "  public <methods>;",
            "}")
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(ClassForIf.class);
              assertThat(classSubject, isPresent());
              MethodSubject methodSubject =
                  classSubject.uniqueMethodWithOriginalName("publicMethod");
              assertThat(methodSubject, isPresent());
              methodSubject = classSubject.uniqueMethodWithOriginalName("nonPublicMethod");
              assertThat(methodSubject, not(isPresent()));

              classSubject = inspector.clazz(ClassForSubsequent.class);
              assertThat(classSubject, isPresent());
              methodSubject = classSubject.uniqueMethodWithOriginalName("publicMethod");
              assertThat(methodSubject, isPresent());
              methodSubject = classSubject.uniqueMethodWithOriginalName("nonPublicMethod");
              assertThat(methodSubject, not(isPresent()));
            });
  }

  @Test
  public void ifOnPublic_keepOnNonPublic() throws Exception {
    assumeFalse(shrinker.isProguard() && parameters.isDexRuntime());

    getTestBuilder()
        .addProgramClasses(CLASSES)
        .addKeepRules(
            "-repackageclasses 'top'",
            "-allowaccessmodification",
            "-keep class **.Main* {",
            "  public static void callIfPublic();",
            "}",
            "-if class **.ClassForIf {",
            "  public <methods>;",
            "}",
            "-keep,allowobfuscation class **.ClassForSubsequent {",
            "  !public <methods>;",
            "}")
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(ClassForIf.class);
              assertThat(classSubject, isPresent());
              MethodSubject methodSubject =
                  classSubject.uniqueMethodWithOriginalName("publicMethod");
              assertThat(methodSubject, isPresent());
              methodSubject = classSubject.uniqueMethodWithOriginalName("nonPublicMethod");
              assertThat(methodSubject, not(isPresent()));

              classSubject = inspector.clazz(ClassForSubsequent.class);
              assertThat(classSubject, isPresent());
              methodSubject = classSubject.uniqueMethodWithOriginalName("publicMethod");
              assertThat(methodSubject, not(isPresent()));
              methodSubject = classSubject.uniqueMethodWithOriginalName("nonPublicMethod");
              assertThat(methodSubject, isPresent());
              assertThat(methodSubject, isPackagePrivate());
            });
  }
}
