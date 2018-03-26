// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.FieldSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class AbstractR8KotlinTestBase extends TestBase {

  // This is the name of the Jasmin-generated class which contains the "main" method which will
  // invoke the tested method.
  private static final String JASMIN_MAIN_CLASS = "TestMain";

  @Parameters(name = "allowAccessModification: {0} target: {1}")
  public static Collection<Object[]> data() {
    ImmutableList.Builder<Object[]> builder = new ImmutableList.Builder<>();
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      builder.add(new Object[]{Boolean.TRUE, targetVersion});
      builder.add(new Object[]{Boolean.FALSE, targetVersion});
    }
    return builder.build();
  }

  @Parameter(0) public boolean allowAccessModification;
  @Parameter(1) public KotlinTargetVersion targetVersion;

  private final List<Path> extraClasspath = new ArrayList<>();

  protected void addExtraClasspath(Path path) {
    extraClasspath.add(path);
  }

  protected static void checkMethodIsInvokedAtLeastOnce(DexCode dexCode,
      MethodSignature... methodSignatures) {
    for (MethodSignature methodSignature : methodSignatures) {
      checkMethodIsInvokedAtLeastOnce(dexCode, methodSignature);
    }
  }

  private static void checkMethodIsInvokedAtLeastOnce(DexCode dexCode,
      MethodSignature methodSignature) {
    assertTrue("No invoke to '" + methodSignature.toString() + "'",
        Arrays.stream(dexCode.instructions)
            .filter((instr) -> instr.getMethod() != null)
            .anyMatch((instr) -> instr.getMethod().name.toString().equals(methodSignature.name)));
  }

  protected static void checkMethodIsNeverInvoked(DexCode dexCode,
      MethodSignature... methodSignatures) {
    for (MethodSignature methodSignature : methodSignatures) {
      checkMethodIsNeverInvoked(dexCode, methodSignature);
    }
  }

  private static void checkMethodIsNeverInvoked(DexCode dexCode,
      MethodSignature methodSignature) {
    assertTrue("At least one invoke to '" + methodSignature.toString() + "'",
        Arrays.stream(dexCode.instructions)
            .filter((instr) -> instr.getMethod() != null)
            .noneMatch((instr) -> instr.getMethod().name.toString().equals(methodSignature.name)));
  }

  protected static void checkMethodsPresence(ClassSubject classSubject,
      Map<MethodSignature, Boolean> presenceMap) {
    presenceMap.forEach(((methodSignature, isPresent) -> {
      MethodSubject methodSubject = classSubject.method(methodSignature);
      String methodDesc = methodSignature.toString();
      String failureMessage = isPresent
          ? "Method '" + methodDesc + "' should be present"
          : "Method '" + methodDesc + "' should not be present";

      assertEquals(failureMessage, isPresent, methodSubject.isPresent());
    }));
  }

  protected static ClassSubject checkClassExists(DexInspector inspector, String className) {
    ClassSubject classSubject = inspector.clazz(className);
    assertNotNull(classSubject);
    assertTrue("No class " + className, classSubject.isPresent());
    return classSubject;
  }

  protected static FieldSubject checkFieldIsPresent(ClassSubject classSubject, String fieldType,
      String fieldName) {
    FieldSubject fieldSubject = classSubject.field(fieldType, fieldName);
    assertTrue("No field " + fieldName + " in " + classSubject.getOriginalName(),
        fieldSubject.isPresent());
    return fieldSubject;
  }

  protected static void checkFieldIsAbsent(ClassSubject classSubject, String fieldType,
      String fieldName) {
    FieldSubject fieldSubject = classSubject.field(fieldType, fieldName);
    assertNotNull(fieldSubject);
    assertFalse(fieldSubject.isPresent());
  }

  protected static MethodSubject checkMethodIsPresent(ClassSubject classSubject,
      MethodSignature methodSignature) {
    return checkMethod(classSubject, methodSignature, true);
  }

  protected static void checkMethodIsAbsent(ClassSubject classSubject,
      MethodSignature methodSignature) {
    checkMethod(classSubject, methodSignature, false);
  }

  protected static MethodSubject checkMethod(ClassSubject classSubject,
      MethodSignature methodSignature, boolean isPresent) {
    MethodSubject methodSubject = classSubject.method(methodSignature);
    assertNotNull(methodSubject);

    if (isPresent) {
      assertTrue("No method " + methodSignature.name, methodSubject.isPresent());
    } else {
      assertFalse("Method " + methodSignature.name + " exists", methodSubject.isPresent());
    }
    return methodSubject;
  }

  protected static DexCode getDexCode(MethodSubject methodSubject) {
    Code code = methodSubject.getMethod().getCode();
    assertNotNull("No code for method " + methodSubject.getMethod().descriptor(), code);
    assertTrue(code.isDexCode());
    return code.asDexCode();
  }

  private String buildProguardRules(String mainClass) {
    ProguardRulesBuilder proguardRules = new ProguardRulesBuilder();
    proguardRules.appendWithLineSeparator(keepMainProguardConfiguration(mainClass));
    proguardRules.dontObfuscate();
    if (allowAccessModification) {
      proguardRules.allowAccessModification();
    }
    return proguardRules.toString();
  }

  protected String keepAllMembers(String className) {
    return "-keep class " + className + " {" + System.lineSeparator()
        + "  *;" + System.lineSeparator()
        + "}";
  }

  protected String keepClassMethod(String className, MethodSignature methodSignature) {
    return "-keep class " + className + " {" + System.lineSeparator()
        + methodSignature.toString() + ";" + System.lineSeparator()
        + "}";
  }

  protected void runTest(String folder, String mainClass, AndroidAppInspector inspector)
      throws Exception {
    runTest(folder, mainClass, null, inspector);
  }

  protected void runTest(String folder, String mainClass, String extraProguardRules,
      AndroidAppInspector inspector) throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());

    String proguardRules = buildProguardRules(mainClass);
    if (extraProguardRules != null) {
      proguardRules += extraProguardRules;
    }

    // Build classpath for compilation (and java execution)
    List<Path> classpath = new ArrayList<>(extraClasspath.size() + 1);
    classpath.add(getKotlinJarFile(folder));
    classpath.add(getJavaJarFile(folder));
    classpath.addAll(extraClasspath);

    // Build with R8
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(classpath);
    AndroidApp app = compileWithR8(builder.build(), proguardRules);

    // Materialize file for execution.
    Path generatedDexFile = temp.getRoot().toPath().resolve("classes.jar");
    app.writeToZip(generatedDexFile, OutputMode.DexIndexed);

    // Run with ART.
    String artOutput =
        ToolHelper.runArtNoVerificationErrors(generatedDexFile.toString(), mainClass);

    // Compare with Java.
    ToolHelper.ProcessResult javaResult = ToolHelper.runJava(classpath, mainClass);
    if (javaResult.exitCode != 0) {
      System.out.println(javaResult.stdout);
      System.err.println(javaResult.stderr);
      fail("JVM failed for: " + mainClass);
    }
    assertEquals("JVM and ART output differ", javaResult.stdout, artOutput);

    inspector.inspectApp(app);
  }

  private Path getKotlinJarFile(String folder) {
    return Paths.get(ToolHelper.TESTS_BUILD_DIR, "kotlinR8TestResources",
        targetVersion.getFolderName(), folder + FileUtils.JAR_EXTENSION);
  }

  private Path getJavaJarFile(String folder) {
    return Paths.get(ToolHelper.TESTS_BUILD_DIR, "kotlinR8TestResources",
        targetVersion.getFolderName(), folder + ".java" + FileUtils.JAR_EXTENSION);
  }

  @FunctionalInterface
  interface AndroidAppInspector {

    void inspectApp(AndroidApp androidApp) throws Exception;
  }

  /**
   * Generates a "main" class which invokes the given static method (which has no argument and
   * return void type). This new class is then added to the test classpath.
   *
   * @param methodClass the class of the static method to invoke
   * @param methodName the name of the static method to invoke
   * @return the name of the generated class
   */
  protected String addMainToClasspath(String methodClass, String methodName) throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder mainClassBuilder =
        builder.addClass(DescriptorUtils.getBinaryNameFromJavaType(JASMIN_MAIN_CLASS));
    mainClassBuilder.addMainMethod(
        "invokestatic " + methodClass + "/" + methodName + "()V",
        "return"
    );

    Path output = writeToZip(builder);
    addExtraClasspath(output);
    return JASMIN_MAIN_CLASS;
  }
}
