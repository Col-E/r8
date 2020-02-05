// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.BeforeClass;
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

  private static final Map<KotlinTargetVersion, Path> kotlinClasses = new HashMap<>();
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0},{1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), KotlinTargetVersion.values());
  }

  public AssertionConfigurationKotlinTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  @BeforeClass
  public static void compileKotlin() throws Exception {
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path ktClasses =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFilesInTestPackage(pkg))
              .compile();
      kotlinClasses.put(targetVersion, ktClasses);
    }
  }

  private void runD8Test(
      ThrowableConsumer<D8TestBuilder> builderConsumer,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> outputLines)
      throws Exception {
    testForD8()
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addProgramFiles(kotlinClasses.get(targetVersion))
        .setMinApi(parameters.getApiLevel())
        .apply(builderConsumer)
        .run(
            parameters.getRuntime(),
            getClass().getPackage().getName() + ".kotlintestclasses.TestClassKt")
        .inspect(inspector)
        .assertSuccessWithOutputLines(outputLines);
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
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addProgramFiles(kotlinClasses.get(targetVersion))
        .addKeepMainRule(testClassKt)
        .addKeepClassAndMembersRules(class1, class2)
        .setMinApi(parameters.getApiLevel())
        .apply(builderConsumer)
        .noMinification()
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
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
      // With R8 the static-put of the kotlin._Assertions.INSTANCE field is removed as well,
      // as is not used.
      assertEquals(
          (isR8 ? 0 : 1),
          subject
              .uniqueMethodWithName("<clinit>")
              .streamInstructions()
              .filter(InstructionSubject::isStaticPut)
              .count());
      assertFalse(
          subject
              .uniqueMethodWithName("<clinit>")
              .streamInstructions()
              .anyMatch(InstructionSubject::isConstNumber));
    } else {
      // In R8 the false (default) value of kotlin._Assertions.ENABLED is propagated.
      assertEquals(
          !isR8,
          subject
              .uniqueMethodWithName("m")
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
      // With R8 the static-put of the kotlin._Assertions.INSTANCE field is removed as is not used.
      assertEquals(
          (isR8 ? 1 : 2),
          subject
              .uniqueMethodWithName("<clinit>")
              .streamInstructions()
              .filter(InstructionSubject::isStaticPut)
              .count());
      assertTrue(
          subject
              .uniqueMethodWithName("<clinit>")
              .streamInstructions()
              .anyMatch(instruction -> instruction.isConstNumber(1)));
    } else {
      assertTrue(
          subject
              .uniqueMethodWithName("m")
              .streamInstructions()
              .anyMatch(InstructionSubject::isThrow));
    }
  }

  private void checkAssertionCodeEnabled(CodeInspector inspector, String clazz, boolean isR8) {
    checkAssertionCodeEnabled(inspector.clazz(clazz), isR8);
  }

  private void checkAssertionCodeLeft(CodeInspector inspector, String clazz, boolean isR8) {
    ClassSubject subject = inspector.clazz(clazz);
    assertThat(subject, isPresent());
    if (subject.getOriginalName().equals("kotlin._Assertions")) {
      // With R8 the static-put of the kotlin._Assertions.INSTANCE field is removed as is not used.
      assertEquals(
          (isR8 ? 1 : 2),
          subject
              .uniqueMethodWithName("<clinit>")
              .streamInstructions()
              .filter(InstructionSubject::isStaticPut)
              .count());
      assertFalse(
          subject
              .uniqueMethodWithName("<clinit>")
              .streamInstructions()
              .anyMatch(InstructionSubject::isConstNumber));
    } else {
      assertTrue(
          subject
              .uniqueMethodWithName("m")
              .streamInstructions()
              .anyMatch(InstructionSubject::isThrow));
    }
  }

  private void checkAssertionCodeRemoved(CodeInspector inspector, boolean isR8) {
    checkAssertionCodeRemoved(inspector, "kotlin._Assertions", isR8);
    checkAssertionCodeRemoved(inspector, class1, isR8);
    checkAssertionCodeRemoved(inspector, class2, isR8);
  }

  private void checkAssertionCodeEnabled(CodeInspector inspector, boolean isR8) {
    checkAssertionCodeEnabled(inspector, "kotlin._Assertions", isR8);
    checkAssertionCodeEnabled(inspector, class1, isR8);
    checkAssertionCodeEnabled(inspector, class2, isR8);
  }

  private void checkAssertionCodeLeft(CodeInspector inspector, boolean isR8) {
    checkAssertionCodeLeft(inspector, "kotlin._Assertions", isR8);
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
                AssertionsConfiguration.Builder::disableAllAssertions),
        inspector -> checkAssertionCodeRemoved(inspector, false),
        noAllAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::disableAllAssertions),
        inspector -> checkAssertionCodeRemoved(inspector, true),
        noAllAssertionsExpectedLines());
    // Compile time enabling assertions gives assertions on Dalvik/Art.
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::enableAllAssertions),
        inspector -> checkAssertionCodeEnabled(inspector, false),
        allAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::enableAllAssertions),
        inspector -> checkAssertionCodeEnabled(inspector, true),
        allAssertionsExpectedLines());
    // Enabling for the "kotlin._Assertions" class should enable all.
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                b -> b.setEnable().setScopeClass("kotlin._Assertions").build()),
        inspector -> checkAssertionCodeEnabled(inspector, false),
        allAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                b -> b.setEnable().setScopeClass("kotlin._Assertions").build()),
        inspector -> checkAssertionCodeEnabled(inspector, true),
        allAssertionsExpectedLines());
    // Enabling for the "kotlin" package should enable all.
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                b -> b.setEnable().setScopePackage("kotlin").build()),
        inspector -> checkAssertionCodeEnabled(inspector, false),
        allAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                b -> b.setEnable().setScopePackage("kotlin").build()),
        inspector -> checkAssertionCodeEnabled(inspector, true),
        allAssertionsExpectedLines());
  }

  @Test
  public void testAssertionsForCf() throws Exception {
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
    // Compile time enabling or disabling assertions means the -ea flag has no effect.
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::enableAllAssertions),
        inspector -> checkAssertionCodeEnabled(inspector, true),
        allAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::enableAllAssertions),
        inspector -> checkAssertionCodeEnabled(inspector, true),
        allAssertionsExpectedLines(),
        true);
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::disableAllAssertions),
        inspector -> checkAssertionCodeRemoved(inspector, true),
        noAllAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::disableAllAssertions),
        inspector -> checkAssertionCodeRemoved(inspector, true),
        noAllAssertionsExpectedLines(),
        true);
  }

  @Test
  public void TestWithModifiedKotlinAssertions() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClassFileData(dumpModifiedKotlinAssertions())
        .addProgramFiles(kotlinClasses.get(targetVersion))
        .setMinApi(parameters.getApiLevel())
        .addAssertionsConfiguration(AssertionsConfiguration.Builder::passthroughAllAssertions)
        .run(
            parameters.getRuntime(),
            getClass().getPackage().getName() + ".kotlintestclasses.TestClassKt")
        .assertSuccessWithOutputLines(noAllAssertionsExpectedLines());
    testForD8()
        .addProgramClassFileData(dumpModifiedKotlinAssertions())
        .addProgramFiles(kotlinClasses.get(targetVersion))
        .setMinApi(parameters.getApiLevel())
        .addAssertionsConfiguration(AssertionsConfiguration.Builder::enableAllAssertions)
        .run(
            parameters.getRuntime(),
            getClass().getPackage().getName() + ".kotlintestclasses.TestClassKt")
        .assertSuccessWithOutputLines(allAssertionsExpectedLines());
    testForD8()
        .addProgramClassFileData(dumpModifiedKotlinAssertions())
        .addProgramFiles(kotlinClasses.get(targetVersion))
        .setMinApi(parameters.getApiLevel())
        .addAssertionsConfiguration(AssertionsConfiguration.Builder::disableAllAssertions)
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
