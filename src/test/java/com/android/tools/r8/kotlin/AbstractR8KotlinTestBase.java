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
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class AbstractR8KotlinTestBase extends TestBase {

  @Parameters(name = "{0}_{1}")
  public static Collection<Object[]> data() {
    ImmutableList.Builder<Object[]> builder = new Builder<>();
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      builder.add(new Object[]{Boolean.TRUE, targetVersion});
      builder.add(new Object[]{Boolean.FALSE, targetVersion});
    }
    return builder.build();
  }

  @Parameter(0) public boolean allowAccessModification;
  @Parameter(1) public KotlinTargetVersion targetVersion;

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

  protected static MethodSubject checkMethodIsPresent(ClassSubject classSubject,
      MethodSignature methodSignature) {
    return checkMethod(classSubject, methodSignature, true);
  }

  protected static MethodSubject checkMethodIsAbsent(ClassSubject classSubject,
      MethodSignature methodSignature) {
    return checkMethod(classSubject, methodSignature, false);
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

  protected String keepClassMethod(String className, MethodSignature methodSignature) {
    return "-keep class " + className + " {" + System.lineSeparator() +
        methodSignature.toString() + ";" + System.lineSeparator() + "}";
  }

  protected void runTest(String folder, String mainClass, AndroidAppInspector inspector)
      throws Exception {
    runTest(folder, mainClass, null, inspector);
  }

  protected void runTest(String folder, String mainClass, String extraProguardRules,
      AndroidAppInspector inspector) throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());

    Path jarFile = getJarFile(folder);

    String proguardRules = buildProguardRules(mainClass);
    if (extraProguardRules != null) {
      proguardRules += extraProguardRules;
    }

    // Build with R8
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(jarFile);
    AndroidApp app = compileWithR8(builder.build(), proguardRules.toString());

    // Materialize file for execution.
    Path generatedDexFile = temp.getRoot().toPath().resolve("classes.jar");
    app.writeToZip(generatedDexFile, OutputMode.DexIndexed);

    // Run with ART.
    String artOutput =
        ToolHelper.runArtNoVerificationErrors(generatedDexFile.toString(), mainClass);

    // Compare with Java.
    ToolHelper.ProcessResult javaResult = ToolHelper.runJava(jarFile, mainClass);
    if (javaResult.exitCode != 0) {
      System.out.println(javaResult.stdout);
      System.err.println(javaResult.stderr);
      fail("JVM failed for: " + mainClass);
    }
    assertEquals("JVM and ART output differ", javaResult.stdout, artOutput);

    inspector.inspectApp(app);
  }

  private Path getJarFile(String folder) {
    return Paths.get(ToolHelper.TESTS_BUILD_DIR, "kotlinR8TestResources",
        targetVersion.getFolderName(), folder + FileUtils.JAR_EXTENSION);
  }

  @FunctionalInterface
  interface AndroidAppInspector {

    void inspectApp(AndroidApp androidApp) throws Exception;
  }
}
