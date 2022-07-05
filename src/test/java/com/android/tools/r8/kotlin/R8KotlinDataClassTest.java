// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_5_0;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_6_0;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
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

  private static final Consumer<InternalOptions> disableClassInliner =
      o -> o.enableClassInlining = false;

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
  public void testDataclassGettersOnly() throws Exception {
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
              if (allowAccessModification) {
                checkClassIsRemoved(inspector, TEST_DATA_CLASS.getClassName());
              } else {
                ClassSubject dataClass =
                    checkClassIsKept(inspector, TEST_DATA_CLASS.getClassName());

                // Getters should be removed after inlining, which is possible only if access is
                // relaxed.
                checkMethodIsKept(dataClass, NAME_GETTER_METHOD);
                checkMethodIsKept(dataClass, AGE_GETTER_METHOD);

                // No use of componentN functions.
                checkMethodIsRemoved(dataClass, COMPONENT1_METHOD);
                checkMethodIsRemoved(dataClass, COMPONENT2_METHOD);

                // No use of copy functions.
                checkMethodIsRemoved(dataClass, COPY_METHOD);
                checkMethodIsRemoved(dataClass, COPY_DEFAULT_METHOD);
              }
            });
  }

  @Test
  public void testDataclassComponentOnly() throws Exception {
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
              if (allowAccessModification) {
                checkClassIsRemoved(inspector, TEST_DATA_CLASS.getClassName());
              } else {
                ClassSubject dataClass =
                    checkClassIsKept(inspector, TEST_DATA_CLASS.getClassName());

                // ComponentN functions should be removed after inlining, which is possible only if
                // access is relaxed.
                checkMethodIsKept(dataClass, COMPONENT1_METHOD);
                checkMethodIsKept(dataClass, COMPONENT2_METHOD);

                // No use of getter.
                checkMethodIsRemoved(dataClass, NAME_GETTER_METHOD);
                checkMethodIsRemoved(dataClass, AGE_GETTER_METHOD);

                // No use of copy functions.
                checkMethodIsRemoved(dataClass, COPY_METHOD);
                checkMethodIsRemoved(dataClass, COPY_DEFAULT_METHOD);
              }
            });
  }

  @Test
  public void testDataclassComponentPartial() throws Exception {
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
              if (allowAccessModification) {
                checkClassIsRemoved(inspector, TEST_DATA_CLASS.getClassName());
              } else {
                ClassSubject dataClass =
                    checkClassIsKept(inspector, TEST_DATA_CLASS.getClassName());
                checkMethodIsKept(dataClass, COMPONENT2_METHOD);

                // Function component1 is not used.
                checkMethodIsRemoved(dataClass, COMPONENT1_METHOD);

                // No use of getter.
                checkMethodIsRemoved(dataClass, NAME_GETTER_METHOD);
                checkMethodIsRemoved(dataClass, AGE_GETTER_METHOD);

                // No use of copy functions.
                checkMethodIsRemoved(dataClass, COPY_METHOD);
                checkMethodIsRemoved(dataClass, COPY_DEFAULT_METHOD);
              }

              ClassSubject classSubject = checkClassIsKept(inspector, mainClassName);
              MethodSubject testMethod = checkMethodIsKept(classSubject, testMethodSignature);
              if (allowAccessModification) {
                checkMethodIsNeverInvoked(testMethod, COMPONENT2_METHOD);
              } else {
                checkMethodIsInvokedAtLeastOnce(testMethod, COMPONENT2_METHOD);
              }
            });
  }

  @Test
  public void testDataclassCopyIsRemovedIfNotUsed() throws Exception {
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
              if (allowAccessModification) {
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
  public void testDataclassCopyDefaultIsRemovedIfNotUsed() throws Exception {
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
              // TODO(b/210828502): Investigate why Person is not removed with kotlin 1.7 and 1.8.
              if (allowAccessModification
                  && (kotlinc.isOneOf(KOTLINC_1_5_0, KOTLINC_1_6_0)
                      || testParameters.isDexRuntime())) {
                checkClassIsRemoved(inspector, TEST_DATA_CLASS.getClassName());
              } else {
                ClassSubject dataClass =
                    checkClassIsKept(inspector, TEST_DATA_CLASS.getClassName());
                checkMethodIsRemoved(dataClass, COPY_DEFAULT_METHOD);
              }
            });
  }
}
