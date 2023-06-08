// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_3_72;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.kotlin.TestKotlinClass;
import com.android.tools.r8.shaking.NoHorizontalClassMergingRule;
import com.android.tools.r8.shaking.NoVerticalClassMergingRule;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinIntrinsicsIdentifierTest extends AbstractR8KotlinNamingTestBase {
  private static final String FOLDER = "intrinsics_identifiers";

  @Parameters(name = "{0}, {1}, allowAccessModification: {2}, minification: {3}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public KotlinIntrinsicsIdentifierTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification,
      boolean minification) {
    super(parameters, kotlinParameters, allowAccessModification, minification);
  }

  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(getKotlinFilesInResource(FOLDER), FOLDER)
          .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect());

  @Test
  public void test_example1() throws Exception {
    // TODO(b/179866251): Update tests.
    assumeTrue(kotlinc.is(KOTLINC_1_3_72));
    TestKotlinClass ex1 = new TestKotlinClass("intrinsics_identifiers.Example1Kt");
    String targetClassName = "ToBeRenamedClass";
    String targetFieldName = "toBeRenamedField";
    String targetMethodName = "toBeRenamedMethod";
    test(ex1, targetClassName, targetFieldName, targetMethodName);
  }

  @Test
  public void test_example2() throws Exception {
    // TODO(b/179866251): Update tests.
    assumeTrue(kotlinc.is(KOTLINC_1_3_72));
    TestKotlinClass ex2 = new TestKotlinClass("intrinsics_identifiers.Example2Kt");
    String targetClassName = "AnotherClass";
    String targetFieldName = "anotherField";
    String targetMethodName = "anotherMethod";
    test(ex2, targetClassName, targetFieldName, targetMethodName);
  }

  @Test
  public void test_example3() throws Exception {
    // TODO(b/179866251): Update tests.
    assumeTrue(kotlinc.is(KOTLINC_1_3_72));
    TestKotlinClass ex3 = new TestKotlinClass("intrinsics_identifiers.Example3Kt");
    String mainClassName = ex3.getClassName();
    TestCompileResult<?, ?> result =
        testForR8(Backend.DEX)
            .addProgramFiles(
                compiledJars.getForConfiguration(kotlinc, targetVersion),
                kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(getJavaJarFile(FOLDER))
            .addKeepMainRule(mainClassName)
            .addKeepRules(
                "-keepconstantarguments class kotlin.jvm.internal.Intrinsics {",
                "  *** checkParameterIsNotNull(...);",
                "}")
            .allowDiagnosticWarningMessages()
            .enableProguardTestOptions()
            .minification(minification)
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."));
    CodeInspector codeInspector = result.inspector();
    MethodSubject main = codeInspector.clazz(ex3.getClassName()).mainMethod();
    assertThat(main, isPresent());
    verifyKotlinIntrinsicsRenamed(codeInspector, main);
  }

  private void verifyKotlinIntrinsicsRenamed(CodeInspector inspector, MethodSubject main) {
    Iterator<InstructionSubject> it = main.iterateInstructions(InstructionSubject::isInvokeStatic);
    assertTrue(it.hasNext());
    boolean metKotlinIntrinsicsNullChecks = false;
    while (it.hasNext()) {
      DexMethod invokedMethod = it.next().getMethod();
      if (invokedMethod.holder.getTypeName().contains("java.net")) {
        continue;
      }
      ClassSubject invokedMethodHolderSubject =
          inspector.clazz(invokedMethod.holder.toSourceString());
      assertThat(invokedMethodHolderSubject, isPresent());
      assertEquals(minification, invokedMethodHolderSubject.isRenamed());
      MethodSubject invokedMethodSubject = invokedMethodHolderSubject.method(
          invokedMethod.proto.returnType.toSourceString(),
          invokedMethod.name.toString(),
          Arrays.stream(invokedMethod.proto.parameters.values)
              .map(DexType::toSourceString)
              .collect(Collectors.toList()));
      assertThat(invokedMethodSubject, isPresent());
      assertEquals(minification, invokedMethodSubject.isRenamed());
      if (invokedMethodSubject.getOriginalName().startsWith("check")
          && invokedMethodSubject.getOriginalName().endsWith("Null")
          && invokedMethodHolderSubject.getOriginalDescriptor()
              .contains("kotlin/jvm/internal/Intrinsics")) {
        metKotlinIntrinsicsNullChecks = true;
      }
    }
    assertTrue(metKotlinIntrinsicsNullChecks);
  }

  private void test(
      TestKotlinClass testMain,
      String targetClassName,
      String targetFieldName,
      String targetMethodName) throws Exception {
    String mainClassName = testMain.getClassName();
    SingleTestRunResult<?> result =
        testForR8(Backend.DEX)
            .addProgramFiles(
                compiledJars.getForConfiguration(kotlinc, targetVersion),
                kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(getJavaJarFile(FOLDER))
            .enableProguardTestOptions()
            .addKeepMainRule(mainClassName)
            .addKeepRules(
                StringUtils.lines(
                    "-neverclassinline class **." + targetClassName,
                    "-" + NoVerticalClassMergingRule.RULE_NAME + " class **." + targetClassName,
                    "-" + NoHorizontalClassMergingRule.RULE_NAME + " class **." + targetClassName,
                    "-neverinline class **." + targetClassName + " { <methods>; }",
                    "-keepconstantarguments class kotlin.jvm.internal.Intrinsics {",
                    "  *** checkParameterIsNotNull(...);",
                    "}",
                    "-neversinglecallerinline class kotlin.jvm.internal.Intrinsics {",
                    "  *** checkParameterIsNotNull(...);",
                    "}"))
            .addNeverSingleCallerInlineAnnotations()
            .allowDiagnosticWarningMessages()
            .minification(minification)
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .run(testParameters.getRuntime(), mainClassName);
    CodeInspector codeInspector = result.inspector();

    MethodSubject main = codeInspector.clazz(testMain.getClassName()).mainMethod();
    assertThat(main, isPresent());
    verifyKotlinIntrinsicsRenamed(codeInspector, main);
    // Examine all const-string and verify that identifiers are not introduced.
    Iterator<InstructionSubject> it =
        main.iterateInstructions(i -> i.isConstString(JumboStringMode.ALLOW));
    assertTrue(it.hasNext());
    while (it.hasNext()) {
      String identifier = it.next().getConstString();
      if (identifier.contains("arg")) {
        continue;
      }
      assertEquals(!minification, identifier.equals(targetMethodName));
      assertEquals(!minification, identifier.equals(targetFieldName));
    }

    targetClassName = FOLDER + "." + targetClassName;
    ClassSubject clazz = minification
        ? checkClassIsRenamed(codeInspector, targetClassName)
        : checkClassIsNotRenamed(codeInspector, targetClassName);
    if (minification) {
      checkFieldIsRenamed(clazz, targetFieldName);
      checkMethodIsRenamed(clazz, targetMethodName);
    } else {
      checkFieldIsNotRenamed(clazz, targetFieldName);
      checkMethodIsNotRenamed(clazz, targetMethodName);
    }
  }

}
