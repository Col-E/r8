// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class AssertionConfigurationKotlinTestBase extends KotlinTestBase {

  protected static final Package pkg = AssertionConfigurationKotlinTestBase.class.getPackage();
  protected static final String kotlintestclasesPackage = pkg.getName() + ".kotlintestclasses";
  protected static final String testClassKt = kotlintestclasesPackage + ".TestClassKt";
  protected static final String class1 = kotlintestclasesPackage + ".Class1";
  protected static final String class2 = kotlintestclasesPackage + ".Class2";

  private static final KotlinCompileMemoizer kotlinWithJvmAssertions =
      getCompileMemoizer(getKotlinFilesForPackage())
          .configure(kotlinCompilerTool -> kotlinCompilerTool.setUseJvmAssertions(true));
  private static final KotlinCompileMemoizer kotlinWithoutJvmAssertions =
      getCompileMemoizer(getKotlinFilesForPackage())
          .configure(kotlinCompilerTool -> kotlinCompilerTool.setUseJvmAssertions(false));

  protected final TestParameters parameters;
  protected final boolean kotlinStdlibAsLibrary;
  protected final boolean useJvmAssertions;
  protected final KotlinCompileMemoizer compiledForAssertions;

  public AssertionConfigurationKotlinTestBase(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean kotlinStdlibAsClasspath,
      boolean useJvmAssertions) {
    super(kotlinParameters);
    this.parameters = parameters;
    this.kotlinStdlibAsLibrary = kotlinStdlibAsClasspath;
    this.useJvmAssertions = useJvmAssertions;
    this.compiledForAssertions =
        useJvmAssertions ? kotlinWithJvmAssertions : kotlinWithoutJvmAssertions;
  }

  private static List<Path> getKotlinFilesForPackage() {
    try {
      return getKotlinFilesInTestPackage(pkg);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Path kotlinStdlibLibraryForRuntime() throws Exception {
    Path kotlinStdlibCf = kotlinc.getKotlinStdlibJar();
    if (parameters.getRuntime().isCf()) {
      return kotlinStdlibCf;
    }

    Path kotlinStdlibDex = temp.newFolder().toPath().resolve("kotlin-stdlib-dex.jar");
    testForD8()
        .addProgramFiles(kotlinStdlibCf)
        .setMinApi(parameters)
        .compile()
        .writeToZip(kotlinStdlibDex);
    return kotlinStdlibDex;
  }

  protected void runD8Test(
      ThrowableConsumer<D8TestBuilder> builderConsumer,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> outputLines)
      throws Exception {
    if (kotlinStdlibAsLibrary) {
      testForD8()
          .addClasspathFiles(kotlinc.getKotlinStdlibJar())
          .addProgramFiles(compiledForAssertions.getForConfiguration(kotlinc, targetVersion))
          .setMinApi(parameters)
          .apply(builderConsumer)
          .addRunClasspathFiles(kotlinStdlibLibraryForRuntime())
          .run(
              parameters.getRuntime(),
              getClass().getPackage().getName() + ".kotlintestclasses.TestClassKt")
          .inspect(inspector)
          .assertSuccessWithOutputLines(outputLines);
    } else {
      testForD8()
          .addProgramFiles(kotlinc.getKotlinStdlibJar())
          .addProgramFiles(compiledForAssertions.getForConfiguration(kotlinc, targetVersion))
          .setMinApi(parameters)
          .apply(builderConsumer)
          .run(
              parameters.getRuntime(),
              getClass().getPackage().getName() + ".kotlintestclasses.TestClassKt")
          .inspect(inspector)
          .assertSuccessWithOutputLines(outputLines);
    }
  }

  protected void runR8Test(
      ThrowableConsumer<R8FullTestBuilder> builderConsumer,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> outputLines)
      throws Exception {
    runR8Test(builderConsumer, inspector, outputLines, false);
  }

  protected void runR8Test(
      ThrowableConsumer<R8FullTestBuilder> builderConsumer,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> outputLines,
      boolean enableJvmAssertions)
      throws Exception {

    testForR8(parameters.getBackend())
        .applyIf(
            kotlinStdlibAsLibrary,
            b -> {
              b.addClasspathFiles(kotlinc.getKotlinStdlibJar());
              b.addRunClasspathFiles(kotlinStdlibLibraryForRuntime());
            },
            b -> b.addProgramFiles(kotlinc.getKotlinStdlibJar()))
        .addClasspathFiles(kotlinc.getKotlinAnnotationJar())
        .addProgramFiles(compiledForAssertions.getForConfiguration(kotlinc, targetVersion))
        .addKeepMainRule(testClassKt)
        .addKeepClassAndMembersRules(class1, class2)
        .setMinApi(parameters)
        .apply(builderConsumer)
        .allowDiagnosticWarningMessages(!kotlinStdlibAsLibrary)
        .addRunClasspathFiles(kotlinStdlibLibraryForRuntime())
        .compile()
        .applyIf(
            !kotlinStdlibAsLibrary,
            result ->
                result.assertAllWarningMessagesMatch(
                    equalTo("Resource 'META-INF/MANIFEST.MF' already exists.")))
        .enableRuntimeAssertions(enableJvmAssertions)
        .run(parameters.getRuntime(), testClassKt)
        .inspect(inspector)
        .assertSuccessWithOutputLines(outputLines);
  }

  protected List<String> allAssertionsExpectedLines() {
    return ImmutableList.of("AssertionError in Class1", "AssertionError in Class2", "DONE");
  }

  protected List<String> noAllAssertionsExpectedLines() {
    return ImmutableList.of("DONE");
  }

  protected void checkAssertionCodeRemoved(ClassSubject subject, boolean isR8) {
    assertThat(subject, isPresent());
    if (subject.getOriginalName().equals("kotlin._Assertions")) {
      assert !kotlinStdlibAsLibrary;
      // With R8 the static-put of the kotlin._Assertions.INSTANCE field is removed as well,
      // as is not used.
      assertEquals(
          (isR8 ? 0 : 1),
          subject
              .uniqueMethodWithOriginalName("<clinit>")
              .streamInstructions()
              .filter(InstructionSubject::isStaticPut)
              .count());
      assertFalse(
          subject
              .uniqueMethodWithOriginalName("<clinit>")
              .streamInstructions()
              .anyMatch(InstructionSubject::isConstNumber));
    } else {
      // The value of kotlin._Assertions.ENABLED is propagated from the assertions configuration
      // for the class kotlin._Assertions.
      assertFalse(
          subject
              .uniqueMethodWithOriginalName("m")
              .streamInstructions()
              .anyMatch(InstructionSubject::isThrow));
    }
  }

  protected void checkAssertionCodeRemoved(CodeInspector inspector, String clazz, boolean isR8) {
    checkAssertionCodeRemoved(inspector.clazz(clazz), isR8);
  }

  protected void checkAssertionCodeEnabled(ClassSubject subject, boolean isR8) {
    assertThat(subject, isPresent());
    if (subject.getOriginalName().equals("kotlin._Assertions")) {
      assert !kotlinStdlibAsLibrary;
      // With R8 the static-put of the kotlin._Assertions.INSTANCE field is removed as is not used.
      assertEquals(
          isR8 ? 1 : 2,
          subject
              .uniqueMethodWithOriginalName("<clinit>")
              .streamInstructions()
              .filter(InstructionSubject::isStaticPut)
              .count());
      assertTrue(
          subject
              .uniqueMethodWithOriginalName("<clinit>")
              .streamInstructions()
              .anyMatch(instruction -> instruction.isConstNumber(1)));
    } else {
      assertTrue(
          subject
              .uniqueMethodWithOriginalName("m")
              .streamInstructions()
              .anyMatch(InstructionSubject::isThrow));
    }
  }

  protected void checkAssertionCodeEnabled(CodeInspector inspector, String clazz, boolean isR8) {
    checkAssertionCodeEnabled(inspector.clazz(clazz), isR8);
  }

  protected void checkAssertionCodeLeft(CodeInspector inspector, String clazz, boolean isR8) {
    ClassSubject subject = inspector.clazz(clazz);
    if (clazz.equals("kotlin._Assertions")) {
      assert !kotlinStdlibAsLibrary;
      if (isR8 && useJvmAssertions) {
        // When JVM assertions are used the class kotlin._Assertions is unused.
        assertThat(subject, not(isPresent()));
        return;
      }
      assertThat(subject, isPresent());
      // With R8 the static-put of the kotlin._Assertions.INSTANCE field is removed as is not used.
      assertEquals(
          isR8 ? 1 : 2,
          subject
              .uniqueMethodWithOriginalName("<clinit>")
              .streamInstructions()
              .filter(InstructionSubject::isStaticPut)
              .count());
      assertFalse(
          subject
              .uniqueMethodWithOriginalName("<clinit>")
              .streamInstructions()
              .anyMatch(InstructionSubject::isConstNumber));
    } else {
      assertThat(subject, isPresent());
      MethodSubject clinit = subject.uniqueMethodWithOriginalName("<clinit>");
      if (useJvmAssertions) {
        assertTrue(clinit.streamInstructions().anyMatch(InstructionSubject::isStaticPut));
      } else {
        assertThat(clinit, not(isPresent()));
      }
      assertTrue(
          subject
              .uniqueMethodWithOriginalName("m")
              .streamInstructions()
              .anyMatch(InstructionSubject::isThrow));
    }
  }

  protected void checkAssertionCodeRemoved(CodeInspector inspector, boolean isR8) {
    if (isR8) {
      assertThat(inspector.clazz("kotlin._Assertions"), not(isPresent()));
    } else if (!kotlinStdlibAsLibrary) {
      checkAssertionCodeRemoved(inspector, "kotlin._Assertions", isR8);
    }
    checkAssertionCodeRemoved(inspector, class1, isR8);
    checkAssertionCodeRemoved(inspector, class2, isR8);
  }

  protected void checkAssertionCodeEnabled(CodeInspector inspector, boolean isR8) {
    if (isR8) {
      assertThat(inspector.clazz("kotlin._Assertions"), not(isPresent()));
    } else if (!kotlinStdlibAsLibrary) {
      checkAssertionCodeEnabled(inspector, "kotlin._Assertions", isR8);
    }
    checkAssertionCodeEnabled(inspector, class1, isR8);
    checkAssertionCodeEnabled(inspector, class2, isR8);
  }

  protected void checkAssertionCodeLeft(CodeInspector inspector, boolean isR8) {
    if (!kotlinStdlibAsLibrary) {
      checkAssertionCodeLeft(inspector, "kotlin._Assertions", isR8);
    }
    checkAssertionCodeLeft(inspector, class1, isR8);
    checkAssertionCodeLeft(inspector, class2, isR8);
  }
}
