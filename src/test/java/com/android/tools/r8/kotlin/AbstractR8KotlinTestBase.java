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
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.FileUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Assume;

// TODO(shertz) also run with backend 1.8
public abstract class AbstractR8KotlinTestBase extends TestBase {

  protected final boolean allowAccessModification;

  protected AbstractR8KotlinTestBase(boolean allowAccessModification) {
    this.allowAccessModification = allowAccessModification;
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

  private static MethodSubject checkMethod(ClassSubject classSubject, String methodName,
      String methodReturnType, List<String> methodParameterTypes, boolean isPresent) {
    return checkMethod(classSubject,
        new MethodSignature(methodName, methodReturnType, methodParameterTypes), isPresent);
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

  private static DexCode extractCodeFor(DexInspector dexInspector, String className,
      String methodName,
      String methodReturnType, List<String> methodParameterTypes) {
    ClassSubject classSubject = checkClassExists(dexInspector, className);
    MethodSubject methodSubject = checkMethodIsPresent(classSubject, methodName, methodReturnType,
        methodParameterTypes);
    return getDexCode(methodSubject);
  }

  private static String getNonOptimizedDexFile(String pkg) {
    return Paths
        .get(ToolHelper.EXAMPLES_KOTLIN_BUILD_DIR, pkg, ToolHelper.DEFAULT_DEX_FILENAME).toString();
  }

  protected static MethodSubject checkMethodIsPresent(ClassSubject classSubject, String methodName,
      String methodReturnType,
      List<String> methodParameterTypes) {
    return checkMethod(classSubject, methodName, methodReturnType, methodParameterTypes, true);
  }

  private String buildProguardRules(String mainClass) {
    ProguardRulesBuilder proguardRules = new ProguardRulesBuilder();
    proguardRules.appendWithLineSeparator(keepMainProguardConfiguration(mainClass));
    proguardRules.appendWithLineSeparator(keepTestMethodProguardConfiguration(mainClass));
    proguardRules.dontObfuscate();
    if (allowAccessModification) {
      proguardRules.allowAccessModification();
    }
    return proguardRules.toString();
  }

  private String keepTestMethodProguardConfiguration(String clazz) {
    return "-keep class " + clazz + " {" + System.lineSeparator() +
        "public void testMethod();" +
        "}";
  }

  protected void buildAndInspect(String folder, String mainClass, AndroidAppInspector inspector)
      throws Exception {
    buildRunAndInspect(folder, mainClass, inspector, true);
  }

  protected void buildRunAndInspect(String folder, String mainClass, AndroidAppInspector inspector)
      throws Exception {
    buildRunAndInspect(folder, mainClass, inspector, false);
  }

  public void buildRunAndInspect(String folder, String mainClass, AndroidAppInspector inspector,
      boolean skipExecution)
      throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());

    Path jarFile =
        Paths.get(ToolHelper.EXAMPLES_KOTLIN_BUILD_DIR, folder + FileUtils.JAR_EXTENSION);

    String proguardRules = buildProguardRules(mainClass);

    // Build with R8
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(jarFile);
    AndroidApp app = compileWithR8(builder.build(), proguardRules.toString());

    // Compare original and generated DEX files.
    String originalDexFile = getNonOptimizedDexFile(folder);

    // Materialize file for execution.
    Path generatedDexFile = temp.getRoot().toPath().resolve("classes.jar");
    app.writeToZip(generatedDexFile, OutputMode.DexIndexed);

    if (!skipExecution) {

      // Run with ART.
      String artOutput = ToolHelper
          .checkArtOutputIdentical(originalDexFile, generatedDexFile.toString(), mainClass,
              ToolHelper.getDexVm());

      // Compare with Java.
      ToolHelper.ProcessResult javaResult = ToolHelper.runJava(jarFile, mainClass);
      if (javaResult.exitCode != 0) {
        System.out.println(javaResult.stdout);
        System.err.println(javaResult.stderr);
        fail("JVM failed for: " + mainClass);
      }
      assertEquals("JVM and ART output differ", javaResult.stdout, artOutput);
    }

    inspector.inspectApp(app);
  }

  @FunctionalInterface
  interface AndroidAppInspector {

    void inspectApp(AndroidApp androidApp) throws Exception;
  }
}
