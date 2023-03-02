// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.apimodel;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassValueTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines(TestClass.class.getTypeName());

  private void computeValuePresent(CodeInspector inspector) {
    assertThat(
        inspector.clazz(ClassValueSub.class).uniqueMethodWithOriginalName("computeValue"),
        isPresent());
  }

  private void computeValueAbsent(CodeInspector inspector) {
    assertThat(
        inspector.clazz(ClassValueSub.class).uniqueMethodWithOriginalName("computeValue"),
        isAbsent());
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .compile()
        .inspect(this::computeValuePresent)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.runtimeWithClassValue(),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .addDontWarn(ClassValue.class)
        .compile()
        .inspect(this::computeValueAbsent)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.runtimeWithClassValue(),
            r -> r.assertFailureWithErrorThatThrows(AbstractMethodError.class),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForProguard(ProguardVersion.V7_0_0)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        // ProGuard warns about the inner class attributes referring to this outer class.
        .addDontWarn(this.getClass().getTypeName())
        // ProGuard also warns about ClassValueSub not having method get.
        .addDontWarn(ClassValueSub.class.getTypeName())
        .compile()
        .inspect(this::computeValueAbsent)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isCfRuntime(),
            r -> r.assertFailureWithErrorThatThrows(AbstractMethodError.class),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testR8KeepExtendsMissingType() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .addDontWarn(ClassValue.class)
        // Try to keep computeValue on classes extending unknown type.
        .addKeepRules("-keep class * extends " + ClassValue.class.getTypeName() + " { *; }")
        .allowUnusedProguardConfigurationRules()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertInfoThatMatches(
                    diagnosticMessage(
                        containsString(
                            "Proguard configuration rule does not match anything: "
                                + "`-keep class * extends "
                                + ClassValue.class.getTypeName()
                                + " {"))))
        .inspect(this::computeValueAbsent)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.runtimeWithClassValue(),
            r -> r.assertFailureWithErrorThatThrows(AbstractMethodError.class),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testProguardKeepExtendsMissingTypeProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime() && parameters.asCfRuntime().getVm() == CfVm.JDK11);
    testForProguard(ProguardVersion.V7_0_0)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        // ProGuard warns about the inner class attributes referring to this outer class.
        .addDontWarn(this.getClass().getTypeName())
        // Just -dontwarn on ClassValue is not sufficient. ProGuard also warns about ClassValueSub
        // not having method get.
        .addDontWarn(ClassValueSub.class.getTypeName())
        // Try to keep computeValue on classes extending unknown type.
        .addKeepRules("-keep class * extends " + ClassValue.class.getTypeName() + " { *; }")
        // TODO(b/261971620): Support extends matching names of missing classes.
        .compile()
        .inspect(this::computeValueAbsent)
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(AbstractMethodError.class);
  }

  @Test
  public void testR8KeepConcreteMethod() throws Exception {
    for (String dontWarn :
        ImmutableList.of(ClassValue.class.getTypeName(), ClassValueSub.class.getTypeName())) {
      testForR8(parameters.getBackend())
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
          .addInnerClasses(getClass())
          .addKeepMainRule(TestClass.class)
          .setMinApi(parameters)
          .addDontWarn(dontWarn)
          .addKeepRules(
              "-keep class "
                  + ClassValueSub.class.getTypeName()
                  + " { ** computeValue(java.lang.Class); }")
          .compile()
          .inspect(this::computeValuePresent)
          .run(parameters.getRuntime(), TestClass.class)
          .applyIf(
              parameters.runtimeWithClassValue(),
              r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
              r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
    }
  }

  @Test
  public void testProguardKeepConcreteMethod() throws Exception {
    assumeTrue(parameters.isCfRuntime() && parameters.asCfRuntime().getVm() == CfVm.JDK11);
    testForProguard(ProguardVersion.V7_0_0)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        // ProGuard warns about the inner class attributes referring to this outer class.
        .addDontWarn(this.getClass().getTypeName())
        // Just -dontwarn on ClassValue is not sufficient. ProGuard also warns about ClassValueSub
        // not having method get.
        .addDontWarn(ClassValueSub.class.getTypeName())
        .addKeepRules(
            "-keep class "
                + ClassValueSub.class.getTypeName()
                + " { ** computeValue(java.lang.Class); }")
        .compile()
        .inspect(this::computeValuePresent)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isCfRuntime(),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  static class ClassValueSub extends ClassValue<Object> {
    @Override
    protected Object computeValue(Class<?> clazz) {
      return clazz.getTypeName();
    }
  }

  static class TestClass {
    static ClassValueSub classValueSub = new ClassValueSub();

    public static void main(String[] args) {
      System.out.println(classValueSub.get(TestClass.class));
    }
  }
}
