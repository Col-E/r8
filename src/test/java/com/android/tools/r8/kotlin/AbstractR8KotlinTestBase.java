// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Assume;

public abstract class AbstractR8KotlinTestBase extends KotlinTestBase {

  // This is the name of the Jasmin-generated class which contains the "main" method which will
  // invoke the tested method.
  private static final String JASMIN_MAIN_CLASS = "TestMain";

  protected final boolean allowAccessModification;

  private final List<Path> classpath = new ArrayList<>();
  private final List<Path> extraClasspath = new ArrayList<>();

  protected final TestParameters testParameters;

  protected AbstractR8KotlinTestBase(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(kotlinParameters);
    this.testParameters = parameters;
    this.allowAccessModification = allowAccessModification;
  }

  protected void addExtraClasspath(Path path) {
    extraClasspath.add(path);
  }

  protected static void checkMethodIsInvokedAtLeastOnce(
      MethodSubject subject, MethodSignature... methodSignatures) {
    for (MethodSignature methodSignature : methodSignatures) {
      checkMethodIsInvokedAtLeastOnce(subject, methodSignature);
    }
  }

  private static void checkMethodIsInvokedAtLeastOnce(
      MethodSubject subject, MethodSignature methodSignature) {
    assertTrue(
        "No invoke to '" + methodSignature.toString() + "'",
        subject
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .anyMatch(
                instructionSubject ->
                    instructionSubject
                        .getMethod()
                        .getName()
                        .toString()
                        .equals(methodSignature.name)));
  }

  protected static void checkMethodIsNeverInvoked(
      MethodSubject subject, MethodSignature... methodSignatures) {
    for (MethodSignature methodSignature : methodSignatures) {
      checkMethodIsNeverInvoked(subject, methodSignature);
    }
  }

  private static void checkMethodIsNeverInvoked(
      MethodSubject subject, MethodSignature methodSignature) {
    assertTrue(
        "At least one invoke to '" + methodSignature.toString() + "'",
        subject
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .noneMatch(
                instructionSubject ->
                    instructionSubject
                        .getMethod()
                        .getName()
                        .toString()
                        .equals(methodSignature.name)));
  }

  protected static void checkMethodsPresence(
      ClassSubject classSubject, Map<MethodSignature, Boolean> presenceMap) {
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

  protected void checkClassIsRemoved(CodeInspector inspector, String className) {
    checkClassExistsInInput(className);
    ClassSubject classSubject = inspector.clazz(className);
    assertNotNull(classSubject);
    assertThat(classSubject, not(isPresent()));
  }

  protected FieldSubject checkFieldIsKept(
      ClassSubject classSubject, String fieldType, String fieldName) {
    // Field must exist in the input.
    checkFieldPresenceInInput(classSubject.getOriginalName(), fieldType, fieldName, true);
    FieldSubject fieldSubject = classSubject.field(fieldType, fieldName);
    assertTrue("No field " + fieldName + " in " + classSubject.getOriginalName(),
        fieldSubject.isPresent());
    return fieldSubject;
  }

  protected FieldSubject checkFieldIsKept(ClassSubject classSubject, String fieldName) {
    FieldSubject fieldSubject = classSubject.uniqueFieldWithOriginalName(fieldName);
    assertThat(fieldSubject, isPresent());
    return fieldSubject;
  }

  protected void checkFieldIsAbsent(ClassSubject classSubject, String fieldType, String fieldName) {
    // Field must NOT exist in the input.
    checkFieldPresenceInInput(classSubject.getOriginalName(), fieldType, fieldName, false);
    FieldSubject fieldSubject = classSubject.field(fieldType, fieldName);
    assertNotNull(fieldSubject);
    assertFalse(fieldSubject.isPresent());
  }

  protected void checkMethodIsAbsent(ClassSubject classSubject, MethodSignature methodSignature) {
    checkMethodPresenceInInput(classSubject.getOriginalName(), methodSignature, false);
    checkMethodPresenceInOutput(classSubject, methodSignature, false);
  }

  protected MethodSubject checkMethodIsKept(
      ClassSubject classSubject, MethodSignature methodSignature) {
    checkMethodPresenceInInput(classSubject.getOriginalName(), methodSignature, true);
    return checkMethodIsKeptOrRemoved(classSubject, methodSignature, true);
  }

  protected MethodSubject checkMethodIsKept(ClassSubject classSubject, String methodName) {
    MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName(methodName);
    assertThat(methodSubject, isPresent());
    return methodSubject;
  }

  protected void checkMethodIsRemoved(ClassSubject classSubject, MethodSignature methodSignature) {
    checkMethodPresenceInInput(classSubject.getOriginalName(), methodSignature, true);
    checkMethodIsKeptOrRemoved(classSubject, methodSignature, false);
  }

  protected MethodSubject checkMethodIsKeptOrRemoved(
      ClassSubject classSubject, MethodSignature methodSignature, boolean isPresent) {
    checkMethodPresenceInInput(classSubject.getOriginalName(), methodSignature, true);
    return checkMethodPresenceInOutput(classSubject, methodSignature, isPresent);
  }

  private MethodSubject checkMethodPresenceInOutput(
      ClassSubject classSubject, MethodSignature methodSignature, boolean isPresent) {
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

  protected String keepClassMethod(String className, MethodSignature methodSignature) {
    return StringUtils.lines(
        "-keep class " + className + " {",
        methodSignature.toString() + ";",
        "}");
  }

  protected String neverInlineMethod(String className, MethodSignature methodSignature) {
    return StringUtils.lines(
        "-neverinline class " + className + " {",
        methodSignature.toString() + ";",
        "}");
  }

  protected R8TestRunResult runTest(String folder, String mainClass) throws Exception {
    return runTest(folder, mainClass, null);
  }

  protected R8TestRunResult runTest(
      String folder, String mainClass, ThrowableConsumer<R8FullTestBuilder> configuration)
      throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported() || ToolHelper.compareAgaintsGoldenFiles());

    Path kotlinJarFile =
        getCompileMemoizer(getKotlinFilesInResource(folder), folder)
            .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect())
            .getForConfiguration(kotlinc, targetVersion);

    // Build classpath for compilation (and java execution)
    classpath.clear();
    classpath.add(kotlinJarFile);
    classpath.add(getJavaJarFile(folder));
    classpath.add(kotlinc.getKotlinAnnotationJar());
    classpath.addAll(extraClasspath);

    // Compare with Java.
    ToolHelper.ProcessResult javaResult = ToolHelper.runJava(classpath, mainClass);
    if (javaResult.exitCode != 0) {
      System.out.println(javaResult.stdout);
      System.err.println(javaResult.stderr);
      fail("JVM failed for: " + mainClass);
    }

    // Build with R8
    return testForR8(testParameters.getBackend())
        .addProgramFiles(classpath)
        .addKeepMainRule(mainClass)
        .allowAccessModification(allowAccessModification)
        .allowDiagnosticWarningMessages()
        .enableProguardTestOptions()
        .addDontObfuscate()
        .setMinApi(testParameters)
        .apply(configuration)
        .compile()
        .assertAllWarningMessagesMatch(
            containsString("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(testParameters.getRuntime(), mainClass)
        .assertSuccessWithOutput(javaResult.stdout);
  }

  protected void checkClassExistsInInput(String className) {
    if (!AsmUtils.doesClassExist(classpath, className)) {
      throw new AssertionError("Class " + className + " does not exist in input");
    }
  }

  protected void checkMethodPresenceInInput(
      String className, MethodSignature methodSignature, boolean isPresent) {
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

  private void checkFieldPresenceInInput(
      String className, String fieldType, String fieldName, boolean isPresent) {
    boolean foundField = AsmUtils.doesFieldExist(classpath, className, fieldName, fieldType);
    if (isPresent != foundField) {
      throw new AssertionError(
          "Field " + fieldName + " " + (foundField ? "exists" : "does not exist")
              + " in input class " + className + " but is expected to be "
              + (isPresent ? "present" : "absent"));
    }
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
