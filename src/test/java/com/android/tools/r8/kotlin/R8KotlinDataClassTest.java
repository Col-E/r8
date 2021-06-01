// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.kotlin.TestKotlinClass.Visibility;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class R8KotlinDataClassTest extends AbstractR8KotlinTestBase {

  private static final TestKotlinDataClass TEST_DATA_CLASS =
      new TestKotlinDataClass("dataclass.Person")
      .addProperty("name", "java.lang.String", Visibility.PUBLIC)
      .addProperty("age", "int", Visibility.PUBLIC);

  private static final MethodSignature NAME_GETTER_METHOD =
      TEST_DATA_CLASS.getGetterForProperty("name");
  private static final MethodSignature AGE_GETTER_METHOD =
      TEST_DATA_CLASS.getGetterForProperty("age");

  private static final MethodSignature COMPONENT1_METHOD =
      TEST_DATA_CLASS.getComponentNFunctionForProperty("name");
  private static final MethodSignature COMPONENT2_METHOD =
      TEST_DATA_CLASS.getComponentNFunctionForProperty("age");
  private static final MethodSignature COPY_METHOD = TEST_DATA_CLASS.getCopySignature();
  private static final MethodSignature COPY_DEFAULT_METHOD =
      TEST_DATA_CLASS.getCopyDefaultSignature();

  private Consumer<InternalOptions> disableClassInliner = o -> o.enableClassInlining = false;

  @Parameterized.Parameters(name = "{0}, {1}, allowAccessModification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public R8KotlinDataClassTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(parameters, kotlinParameters, allowAccessModification);
  }

  @Test
  public void test_dataclass_gettersOnly() throws Exception {
    // TODO(b/179866251): Allow for CF code.
    assumeTrue(testParameters.isDexRuntime());
    String mainClassName = "dataclass.MainGettersOnlyKt";
    MethodSignature testMethodSignature =
        new MethodSignature("testDataClassGetters", "void", Collections.emptyList());
    runTest(
            "dataclass",
            mainClassName,
            testBuilder ->
                testBuilder
                    .addKeepRules(keepClassMethod(mainClassName, testMethodSignature))
                    .addOptionsModification(disableClassInliner))
        .inspect(
            inspector -> {
              if (allowAccessModification
                  && kotlinParameters.is(
                      KotlinCompilerVersion.KOTLINC_1_5_0_M2, KotlinTargetVersion.JAVA_8)) {
                checkClassIsRemoved(inspector, TEST_DATA_CLASS.getClassName());
              } else {
                ClassSubject dataClass =
                    checkClassIsKept(inspector, TEST_DATA_CLASS.getClassName());

                // Getters should be removed after inlining, which is possible only if access is
                // relaxed.
                boolean areGetterPresent = !allowAccessModification;
                checkMethodIsKeptOrRemoved(dataClass, NAME_GETTER_METHOD, areGetterPresent);
                checkMethodIsKeptOrRemoved(dataClass, AGE_GETTER_METHOD, areGetterPresent);

                // No use of componentN functions.
                checkMethodIsRemoved(dataClass, COMPONENT1_METHOD);
                checkMethodIsRemoved(dataClass, COMPONENT2_METHOD);

                // No use of copy functions.
                checkMethodIsRemoved(dataClass, COPY_METHOD);
                checkMethodIsRemoved(dataClass, COPY_DEFAULT_METHOD);
              }

              ClassSubject classSubject = checkClassIsKept(inspector, mainClassName);
              MethodSubject testMethod = checkMethodIsKept(classSubject, testMethodSignature);
              DexCode dexCode = getDexCode(testMethod);
              if (allowAccessModification) {
                // Both getters should be inlined
                checkMethodIsNeverInvoked(dexCode, NAME_GETTER_METHOD, AGE_GETTER_METHOD);
              } else {
                checkMethodIsInvokedAtLeastOnce(dexCode, NAME_GETTER_METHOD, AGE_GETTER_METHOD);
              }
            });
  }

  @Test
  public void test_dataclass_componentOnly() throws Exception {
    // TODO(b/179866251): Allow for CF code.
    assumeTrue(testParameters.isDexRuntime());
    String mainClassName = "dataclass.MainComponentOnlyKt";
    MethodSignature testMethodSignature =
        new MethodSignature("testAllDataClassComponentFunctions", "void", Collections.emptyList());
    runTest(
            "dataclass",
            mainClassName,
            testBuilder ->
                testBuilder
                    .addKeepRules(keepClassMethod(mainClassName, testMethodSignature))
                    .addOptionsModification(disableClassInliner))
        .inspect(
            inspector -> {
              if (allowAccessModification
                  && kotlinParameters.is(
                      KotlinCompilerVersion.KOTLINC_1_5_0_M2, KotlinTargetVersion.JAVA_8)) {
                checkClassIsRemoved(inspector, TEST_DATA_CLASS.getClassName());
              } else {
                ClassSubject dataClass =
                    checkClassIsKept(inspector, TEST_DATA_CLASS.getClassName());

                // ComponentN functions should be removed after inlining, which is possible only if
                // access is relaxed.
                boolean areComponentMethodsPresent = !allowAccessModification;
                checkMethodIsKeptOrRemoved(
                    dataClass, COMPONENT1_METHOD, areComponentMethodsPresent);
                checkMethodIsKeptOrRemoved(
                    dataClass, COMPONENT2_METHOD, areComponentMethodsPresent);

                // No use of getter.
                checkMethodIsRemoved(dataClass, NAME_GETTER_METHOD);
                checkMethodIsRemoved(dataClass, AGE_GETTER_METHOD);

                // No use of copy functions.
                checkMethodIsRemoved(dataClass, COPY_METHOD);
                checkMethodIsRemoved(dataClass, COPY_DEFAULT_METHOD);
              }

              ClassSubject classSubject = checkClassIsKept(inspector, mainClassName);
              MethodSubject testMethod = checkMethodIsKept(classSubject, testMethodSignature);
              DexCode dexCode = getDexCode(testMethod);
              if (allowAccessModification) {
                checkMethodIsNeverInvoked(dexCode, COMPONENT1_METHOD, COMPONENT2_METHOD);
              } else {
                checkMethodIsInvokedAtLeastOnce(dexCode, COMPONENT1_METHOD, COMPONENT2_METHOD);
              }
            });
  }

  @Test
  public void test_dataclass_componentPartial() throws Exception {
    // TODO(b/179866251): Allow for CF code.
    assumeTrue(testParameters.isDexRuntime());
    String mainClassName = "dataclass.MainComponentPartialKt";
    MethodSignature testMethodSignature =
        new MethodSignature("testSomeDataClassComponentFunctions", "void", Collections.emptyList());
    runTest(
            "dataclass",
            mainClassName,
            testBuilder ->
                testBuilder
                    .addKeepRules(keepClassMethod(mainClassName, testMethodSignature))
                    .addOptionsModification(disableClassInliner))
        .inspect(
            inspector -> {
              ClassSubject dataClass = checkClassIsKept(inspector, TEST_DATA_CLASS.getClassName());

              boolean component2IsPresent = !allowAccessModification;
              checkMethodIsKeptOrRemoved(dataClass, COMPONENT2_METHOD, component2IsPresent);

              // Function component1 is not used.
              checkMethodIsRemoved(dataClass, COMPONENT1_METHOD);

              // No use of getter.
              checkMethodIsRemoved(dataClass, NAME_GETTER_METHOD);
              checkMethodIsRemoved(dataClass, AGE_GETTER_METHOD);

              // No use of copy functions.
              checkMethodIsRemoved(dataClass, COPY_METHOD);
              checkMethodIsRemoved(dataClass, COPY_DEFAULT_METHOD);

              ClassSubject classSubject = checkClassIsKept(inspector, mainClassName);
              MethodSubject testMethod = checkMethodIsKept(classSubject, testMethodSignature);
              DexCode dexCode = getDexCode(testMethod);
              if (allowAccessModification) {
                checkMethodIsNeverInvoked(dexCode, COMPONENT2_METHOD);
              } else {
                checkMethodIsInvokedAtLeastOnce(dexCode, COMPONENT2_METHOD);
              }
            });
  }

  @Test
  public void test_dataclass_copyIsRemovedIfNotUsed() throws Exception {
    String mainClassName = "dataclass.MainComponentOnlyKt";
    MethodSignature testMethodSignature =
        new MethodSignature("testDataClassCopy", "void", Collections.emptyList());
    runTest(
            "dataclass",
            mainClassName,
            testBuilder ->
                testBuilder
                    .addKeepRules(keepClassMethod(mainClassName, testMethodSignature))
                    .addOptionsModification(disableClassInliner))
        .inspect(
            inspector -> {
              if (testParameters.isDexRuntime()
                  && allowAccessModification
                  && kotlinParameters.is(KotlinCompilerVersion.KOTLINC_1_3_72)) {
                checkClassIsRemoved(inspector, TEST_DATA_CLASS.getClassName());
              } else {
                ClassSubject dataClass =
                    checkClassIsKept(inspector, TEST_DATA_CLASS.getClassName());
                checkMethodIsRemoved(dataClass, COPY_METHOD);
                checkMethodIsRemoved(dataClass, COPY_DEFAULT_METHOD);
              }
            });
  }

  @Test
  public void test_dataclass_copyDefaultIsRemovedIfNotUsed() throws Exception {
    String mainClassName = "dataclass.MainCopyKt";
    MethodSignature testMethodSignature =
        new MethodSignature("testDataClassCopyWithDefault", "void", Collections.emptyList());
    runTest(
            "dataclass",
            mainClassName,
            testBuilder ->
                testBuilder
                    .addKeepRules(keepClassMethod(mainClassName, testMethodSignature))
                    .addOptionsModification(disableClassInliner))
        .inspect(
            inspector -> {
              ClassSubject dataClass = checkClassIsKept(inspector, TEST_DATA_CLASS.getClassName());
              checkMethodIsRemoved(dataClass, COPY_DEFAULT_METHOD);
            });
  }
}
