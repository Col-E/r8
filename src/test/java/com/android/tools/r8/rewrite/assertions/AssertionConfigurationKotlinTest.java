// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
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

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class AssertionConfigurationKotlinTest extends KotlinTestBase implements Opcodes {

  private static final Package pkg = AssertionConfigurationKotlinTest.class.getPackage();
  private static final String kotlintestclasesPackage = pkg.getName() + ".kotlintestclasses";
  private static final String testClassKt = kotlintestclasesPackage + ".TestClassKt";
  private static final String class1 = kotlintestclasesPackage + ".Class1";
  private static final String class2 = kotlintestclasesPackage + ".Class2";

  private static final KotlinCompileMemoizer kotlinWithJvmAssertions =
      getCompileMemoizer(getKotlinFilesForPackage())
          .configure(kotlinCompilerTool -> kotlinCompilerTool.setUseJvmAssertions(true));
  private static final KotlinCompileMemoizer kotlinWithoutJvmAssertions =
      getCompileMemoizer(getKotlinFilesForPackage())
          .configure(kotlinCompilerTool -> kotlinCompilerTool.setUseJvmAssertions(false));

  private final TestParameters parameters;
  private final boolean kotlinStdlibAsLibrary;
  private final boolean useJvmAssertions;
  private final KotlinCompileMemoizer compiledForAssertions;

  @Parameterized.Parameters(name = "{0}, {1}, kotlin-stdlib as library: {2}, -Xassertions=jvm: {3}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public AssertionConfigurationKotlinTest(
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
        .setMinApi(parameters.getApiLevel())
        .compile()
        .writeToZip(kotlinStdlibDex);
    return kotlinStdlibDex;
  }

  private void runD8Test(
      ThrowableConsumer<D8TestBuilder> builderConsumer,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> outputLines)
      throws Exception {
    if (kotlinStdlibAsLibrary) {
      testForD8()
          .addClasspathFiles(kotlinc.getKotlinStdlibJar())
          .addProgramFiles(compiledForAssertions.getForConfiguration(kotlinc, targetVersion))
          .setMinApi(parameters.getApiLevel())
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
          .setMinApi(parameters.getApiLevel())
          .apply(builderConsumer)
          .run(
              parameters.getRuntime(),
              getClass().getPackage().getName() + ".kotlintestclasses.TestClassKt")
          .inspect(inspector)
          .assertSuccessWithOutputLines(outputLines);
    }
  }

  public void runR8Test(
      ThrowableConsumer<R8FullTestBuilder> builderConsumer,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> outputLines)
      throws Exception {
    runR8Test(builderConsumer, inspector, outputLines, false);
  }

  public void runR8Test(
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
        .setMinApi(parameters.getApiLevel())
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

  private List<String> allAssertionsExpectedLines() {
    return ImmutableList.of("AssertionError in Class1", "AssertionError in Class2", "DONE");
  }

  private List<String> noAllAssertionsExpectedLines() {
    return ImmutableList.of("DONE");
  }

  private void checkAssertionCodeRemoved(ClassSubject subject, boolean isR8) {
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

  private void checkAssertionCodeRemoved(CodeInspector inspector, String clazz, boolean isR8) {
    checkAssertionCodeRemoved(inspector.clazz(clazz), isR8);
  }

  private void checkAssertionCodeEnabled(ClassSubject subject, boolean isR8) {
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

  private void checkAssertionCodeEnabled(CodeInspector inspector, String clazz, boolean isR8) {

    checkAssertionCodeEnabled(inspector.clazz(clazz), isR8);
  }

  private void checkAssertionCodeLeft(CodeInspector inspector, String clazz, boolean isR8) {
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

  private void checkAssertionCodeRemoved(CodeInspector inspector, boolean isR8) {
    if (isR8) {
      assertThat(inspector.clazz("kotlin._Assertions"), not(isPresent()));
    } else if (!kotlinStdlibAsLibrary) {
      checkAssertionCodeRemoved(inspector, "kotlin._Assertions", isR8);
    }
    checkAssertionCodeRemoved(inspector, class1, isR8);
    checkAssertionCodeRemoved(inspector, class2, isR8);
  }

  private void checkAssertionCodeEnabled(CodeInspector inspector, boolean isR8) {
    if (isR8) {
      assertThat(inspector.clazz("kotlin._Assertions"), not(isPresent()));
    } else if (!kotlinStdlibAsLibrary) {
      checkAssertionCodeEnabled(inspector, "kotlin._Assertions", isR8);
    }
    checkAssertionCodeEnabled(inspector, class1, isR8);
    checkAssertionCodeEnabled(inspector, class2, isR8);
  }

  private void checkAssertionCodeLeft(CodeInspector inspector, boolean isR8) {
    if (!kotlinStdlibAsLibrary) {
      checkAssertionCodeLeft(inspector, "kotlin._Assertions", isR8);
    }
    checkAssertionCodeLeft(inspector, class1, isR8);
    checkAssertionCodeLeft(inspector, class2, isR8);
  }

  @Test
  public void testAssertionsForDex() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    // Leaving assertions in or disabling them on Dalvik/Art means no assertions.
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::passthroughAllAssertions),
        inspector -> checkAssertionCodeLeft(inspector, false),
        noAllAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::passthroughAllAssertions),
        inspector -> checkAssertionCodeLeft(inspector, true),
        noAllAssertionsExpectedLines());
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeDisableAllAssertions),
        inspector -> checkAssertionCodeRemoved(inspector, false),
        noAllAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeDisableAllAssertions),
        inspector -> checkAssertionCodeRemoved(inspector, true),
        noAllAssertionsExpectedLines());
    // Compile time enabling assertions gives assertions on Dalvik/Art.
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions),
        inspector -> checkAssertionCodeEnabled(inspector, false),
        allAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions),
        inspector -> checkAssertionCodeEnabled(inspector, true),
        allAssertionsExpectedLines());
    if (useJvmAssertions) {
      // Enabling for the kotlin generated Java classes should enable all.
      runD8Test(
          builder ->
              builder
                  .addAssertionsConfiguration(
                      b -> b.setCompileTimeEnable().setScopeClass(class1).build())
                  .addAssertionsConfiguration(
                      b -> b.setCompileTimeEnable().setScopeClass(class2).build()),
          inspector -> {
            // The default is applied to kotlin._Assertions (which for DEX is remove).
            if (!kotlinStdlibAsLibrary) {
              checkAssertionCodeRemoved(inspector, "kotlin._Assertions", false);
            }
            checkAssertionCodeEnabled(inspector, class1, false);
            checkAssertionCodeEnabled(inspector, class2, false);
          },
          allAssertionsExpectedLines());
    } else {
      // Enabling for the class kotlin._Assertions should enable all.
      runD8Test(
          builder ->
              builder.addAssertionsConfiguration(
                  b -> b.setCompileTimeEnable().setScopeClass("kotlin._Assertions").build()),
          inspector -> checkAssertionCodeEnabled(inspector, false),
          allAssertionsExpectedLines());
    }
    if (useJvmAssertions) {
      // Enabling for the kotlin generated Java classes should enable all.
      runR8Test(
          builder ->
              builder
                  .addAssertionsConfiguration(
                      b -> b.setCompileTimeEnable().setScopeClass(class1).build())
                  .addAssertionsConfiguration(
                      b -> b.setCompileTimeEnable().setScopeClass(class2).build()),
          inspector -> checkAssertionCodeEnabled(inspector, true),
          allAssertionsExpectedLines());
    } else {
      // Enabling for the class kotlin._Assertions should enable all.
      runR8Test(
          builder ->
              builder.addAssertionsConfiguration(
                  b -> b.setCompileTimeEnable().setScopeClass("kotlin._Assertions").build()),
          inspector -> checkAssertionCodeEnabled(inspector, true),
          allAssertionsExpectedLines());
    }
    if (useJvmAssertions) {
      // Enabling for the Java package for the kotlin test classes package should enable all.
      runD8Test(
          builder ->
              builder.addAssertionsConfiguration(
                  b -> b.setCompileTimeEnable().setScopePackage(kotlintestclasesPackage).build()),
          inspector -> {
            // The default is applied to kotlin._Assertions (which for DEX is remove).
            if (!kotlinStdlibAsLibrary) {
              checkAssertionCodeRemoved(inspector, "kotlin._Assertions", false);
            }
            checkAssertionCodeEnabled(inspector, class1, false);
            checkAssertionCodeEnabled(inspector, class2, false);
          },
          allAssertionsExpectedLines());
    } else {
      // Enabling for the kotlin package (containing kotlin._Assertions) should enable all.
      runD8Test(
          builder ->
              builder.addAssertionsConfiguration(
                  b -> b.setCompileTimeEnable().setScopePackage("kotlin").build()),
          inspector -> checkAssertionCodeEnabled(inspector, false),
          allAssertionsExpectedLines());
    }
    if (useJvmAssertions) {
      // Enabling for the Java package for the kotlin test classes package should enable all.
      runR8Test(
          builder ->
              builder.addAssertionsConfiguration(
                  b -> b.setCompileTimeEnable().setScopePackage(kotlintestclasesPackage).build()),
          inspector -> checkAssertionCodeEnabled(inspector, true),
          allAssertionsExpectedLines());
    } else {
      // Enabling for the kotlin package (containing kotlin._Assertions) should enable all.
      runR8Test(
          builder ->
              builder.addAssertionsConfiguration(
                  b -> b.setCompileTimeEnable().setScopePackage("kotlin").build()),
          inspector -> checkAssertionCodeEnabled(inspector, true),
          allAssertionsExpectedLines());
    }
  }

  @Test
  public void testAssertionsForCfEnableWithStackMap() throws Exception {
    Assume.assumeTrue(parameters.isCfRuntime());
    Assume.assumeTrue(useJvmAssertions);
    Assume.assumeTrue(targetVersion == KotlinTargetVersion.JAVA_8);
    // Compile time enabling or disabling assertions means the -ea flag has no effect.
    runR8Test(
        builder -> {
          builder.addAssertionsConfiguration(
              AssertionsConfiguration.Builder::compileTimeEnableAllAssertions);
        },
        inspector -> checkAssertionCodeEnabled(inspector, true),
        allAssertionsExpectedLines());
    runR8Test(
        builder -> {
          builder.addAssertionsConfiguration(
              AssertionsConfiguration.Builder::compileTimeEnableAllAssertions);
        },
        inspector -> checkAssertionCodeEnabled(inspector, true),
        allAssertionsExpectedLines(),
        true);
  }

  @Test
  public void testAssertionsForCfPassThrough() throws Exception {
    Assume.assumeTrue(parameters.isCfRuntime());
    // Leaving assertion code means assertions are controlled by the -ea flag.
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::passthroughAllAssertions),
        inspector -> checkAssertionCodeLeft(inspector, true),
        noAllAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::passthroughAllAssertions),
        inspector -> checkAssertionCodeLeft(inspector, true),
        allAssertionsExpectedLines(),
        true);
  }

  @Test
  public void testAssertionsForCfEnable() throws Exception {
    Assume.assumeTrue(parameters.isCfRuntime());
    // Compile time enabling or disabling assertions means the -ea flag has no effect.
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions),
        inspector -> checkAssertionCodeEnabled(inspector, true),
        allAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions),
        inspector -> checkAssertionCodeEnabled(inspector, true),
        allAssertionsExpectedLines(),
        true);
  }

  @Test
  public void testAssertionsForCfDisable() throws Exception {
    Assume.assumeTrue(parameters.isCfRuntime());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeDisableAllAssertions),
        inspector -> checkAssertionCodeRemoved(inspector, true),
        noAllAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeDisableAllAssertions),
        inspector -> checkAssertionCodeRemoved(inspector, true),
        noAllAssertionsExpectedLines(),
        true);
  }

  @Test
  public void TestWithModifiedKotlinAssertions() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClassFileData(dumpModifiedKotlinAssertions())
        .addProgramFiles(compiledForAssertions.getForConfiguration(kotlinc, targetVersion))
        .setMinApi(parameters.getApiLevel())
        .addAssertionsConfiguration(AssertionsConfiguration.Builder::passthroughAllAssertions)
        .run(
            parameters.getRuntime(),
            getClass().getPackage().getName() + ".kotlintestclasses.TestClassKt")
        .assertSuccessWithOutputLines(noAllAssertionsExpectedLines());
    testForD8()
        .addProgramClassFileData(dumpModifiedKotlinAssertions())
        .addProgramFiles(compiledForAssertions.getForConfiguration(kotlinc, targetVersion))
        .setMinApi(parameters.getApiLevel())
        .addAssertionsConfiguration(AssertionsConfiguration.Builder::compileTimeEnableAllAssertions)
        .run(
            parameters.getRuntime(),
            getClass().getPackage().getName() + ".kotlintestclasses.TestClassKt")
        .assertSuccessWithOutputLines(allAssertionsExpectedLines());
    testForD8()
        .addProgramClassFileData(dumpModifiedKotlinAssertions())
        .addProgramFiles(compiledForAssertions.getForConfiguration(kotlinc, targetVersion))
        .setMinApi(parameters.getApiLevel())
        .addAssertionsConfiguration(
            AssertionsConfiguration.Builder::compileTimeDisableAllAssertions)
        .run(
            parameters.getRuntime(),
            getClass().getPackage().getName() + ".kotlintestclasses.TestClassKt")
        .assertSuccessWithOutputLines(noAllAssertionsExpectedLines());
  }

  // Slightly modified version of kotlin._Assertions to hit all code paths in the assertion
  // rewriter. See "Code added" below.
  public static byte[] dumpModifiedKotlinAssertions() {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    classWriter.visit(
        V1_6,
        ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
        "kotlin/_Assertions",
        null,
        "java/lang/Object",
        null);

    {
      fieldVisitor =
          classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "ENABLED", "Z", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "INSTANCE", "Lkotlin/_Assertions;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC | ACC_DEPRECATED,
              "ENABLED$annotations",
              "()V",
              null,
              null);
      methodVisitor.visitCode();
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(0, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitTypeInsn(NEW, "kotlin/_Assertions");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "kotlin/_Assertions", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ASTORE, 0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          PUTSTATIC, "kotlin/_Assertions", "INSTANCE", "Lkotlin/_Assertions;");

      // Code added (added an additional call to getClass().desiredAssertionStatus() which
      // result is not assigned to ENABLED).
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Class", "desiredAssertionStatus", "()Z", false);
      methodVisitor.visitInsn(POP);
      // End code added.

      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Class", "desiredAssertionStatus", "()Z", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "kotlin/_Assertions", "ENABLED", "Z");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
