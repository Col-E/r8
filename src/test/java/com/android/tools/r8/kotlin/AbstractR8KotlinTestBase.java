// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class AbstractR8KotlinTestBase extends KotlinTestBase {

  // This is the name of the Jasmin-generated class which contains the "main" method which will
  // invoke the tested method.
  private static final String JASMIN_MAIN_CLASS = "TestMain";

  @Parameter(0) public boolean allowAccessModification;
  @Parameter(1) public KotlinTargetVersion targetVersion;

  private final List<Path> classpath = new ArrayList<>();
  private final List<Path> extraClasspath = new ArrayList<>();

  @Parameters(name = "allowAccessModification: {0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(BooleanUtils.values(), KotlinTargetVersion.values());
  }

  public AbstractR8KotlinTestBase() {
    super(KotlinTargetVersion.JAVA_6);
  }

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

  protected ClassSubject checkClassIsKept(CodeInspector inspector, String className) {
    checkClassExistsInInput(className);
    ClassSubject classSubject = inspector.clazz(className);
    assertNotNull(classSubject);
    assertTrue("No class " + className, classSubject.isPresent());
    return classSubject;
  }

  protected FieldSubject checkFieldIsKept(ClassSubject classSubject, String fieldType,
      String fieldName) {
    // Field must exist in the input.
    checkFieldPresenceInInput(classSubject.getOriginalName(), fieldType, fieldName, true);
    FieldSubject fieldSubject = classSubject.field(fieldType, fieldName);
    assertTrue("No field " + fieldName + " in " + classSubject.getOriginalName(),
        fieldSubject.isPresent());
    return fieldSubject;
  }

  protected void checkFieldIsAbsent(ClassSubject classSubject, String fieldType,
      String fieldName) {
    // Field must NOT exist in the input.
    checkFieldPresenceInInput(classSubject.getOriginalName(), fieldType, fieldName, false);
    FieldSubject fieldSubject = classSubject.field(fieldType, fieldName);
    assertNotNull(fieldSubject);
    assertFalse(fieldSubject.isPresent());
  }

  protected void checkMethodIsAbsent(ClassSubject classSubject,
      MethodSignature methodSignature) {
    checkMethodPresenceInInput(classSubject.getOriginalName(), methodSignature, false);
    checkMethodPresenceInOutput(classSubject, methodSignature, false);
  }

  protected MethodSubject checkMethodIsKept(ClassSubject classSubject,
      MethodSignature methodSignature) {
    checkMethodPresenceInInput(classSubject.getOriginalName(), methodSignature, true);
    return checkMethodisKeptOrRemoved(classSubject, methodSignature, true);
  }

  protected void checkMethodIsRemoved(ClassSubject classSubject,
      MethodSignature methodSignature) {
    checkMethodPresenceInInput(classSubject.getOriginalName(), methodSignature, true);
    checkMethodisKeptOrRemoved(classSubject, methodSignature, false);
  }

  protected MethodSubject checkMethodisKeptOrRemoved(ClassSubject classSubject,
      MethodSignature methodSignature, boolean isPresent) {
    checkMethodPresenceInInput(classSubject.getOriginalName(), methodSignature, true);
    return checkMethodPresenceInOutput(classSubject, methodSignature, isPresent);
  }

  private MethodSubject checkMethodPresenceInOutput(ClassSubject classSubject,
      MethodSignature methodSignature, boolean isPresent) {
    MethodSubject methodSubject = classSubject.method(methodSignature);
    assertNotNull(methodSubject);

    String methodSig = methodSignature.name + methodSignature.toDescriptor();
    if (isPresent) {
      assertTrue("No method " + methodSig + " in output", methodSubject.isPresent());
    } else {
      assertFalse("Method " + methodSig + " exists in output", methodSubject.isPresent());
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

  protected void runTest(String folder, String mainClass,
      AndroidAppInspector inspector) throws Exception {
    runTest(folder, mainClass, null, null, inspector);
  }

  protected void runTest(String folder, String mainClass,
      Consumer<InternalOptions> optionsConsumer, AndroidAppInspector inspector) throws Exception {
    runTest(folder, mainClass, null, optionsConsumer, inspector);
  }

  protected void runTest(String folder, String mainClass,
      String extraProguardRules, AndroidAppInspector inspector) throws Exception {
    runTest(folder, mainClass, extraProguardRules, null, inspector);
  }

  protected void runTest(String folder, String mainClass, String extraProguardRules,
      Consumer<InternalOptions> optionsConsumer, AndroidAppInspector inspector) throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported() || ToolHelper.compareAgaintsGoldenFiles());

    String proguardRules = buildProguardRules(mainClass);
    if (extraProguardRules != null) {
      proguardRules += extraProguardRules;
    }

    // Build classpath for compilation (and java execution)
    classpath.clear();
    classpath.add(getKotlinJarFile(folder, targetVersion));
    classpath.add(getJavaJarFile(folder, targetVersion));
    classpath.addAll(extraClasspath);

    // Build with R8
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(classpath);
    R8Command.Builder commandBuilder =
        ToolHelper.prepareR8CommandBuilder(builder.build(), emptyConsumer(Backend.DEX))
            .addLibraryFiles(runtimeJar(Backend.DEX))
            .addProguardConfiguration(ImmutableList.of(proguardRules), Origin.unknown());
    ToolHelper.allowTestProguardOptions(commandBuilder);
    AndroidApp app = ToolHelper.runR8(commandBuilder.build(), optionsConsumer);

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

    if (inspector != null) {
      inspector.inspectApp(app);
    }
  }

  protected void checkClassExistsInInput(String className) {
    if (!AsmUtils.doesClassExist(classpath, className)) {
      throw new AssertionError("Class " + className + " does not exist in input");
    }
  }

  private void checkMethodPresenceInInput(String className, MethodSignature methodSignature,
      boolean isPresent) {
    boolean foundMethod = AsmUtils.doesMethodExist(classpath, className,
        methodSignature.name, methodSignature.toDescriptor());
    if (isPresent != foundMethod) {
      throw new AssertionError(
          "Method " + methodSignature.name + methodSignature.toDescriptor()
              + " " + (foundMethod ? "exists" : "does not exist")
              + " in input class " + className + " but is expected to be "
              + (isPresent ? "present" : "absent"));
    }
  }

  private void checkFieldPresenceInInput(String className, String fieldType, String fieldName,
      boolean isPresent) {
    boolean foundField = AsmUtils.doesFieldExist(classpath, className, fieldName, fieldType);
    if (isPresent != foundField) {
      throw new AssertionError(
          "Field " + fieldName + " " + (foundField ? "exists" : "does not exist")
              + " in input class " + className + " but is expected to be "
              + (isPresent ? "present" : "absent"));
    }
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

    Path output = writeToJar(builder);
    addExtraClasspath(output);
    return JASMIN_MAIN_CLASS;
  }
}
