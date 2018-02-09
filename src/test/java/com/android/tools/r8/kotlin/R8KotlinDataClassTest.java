// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8KotlinDataClassTest extends AbstractR8KotlinTestBase {

  private static final KotlinDataClass TEST_DATA_CLASS = new KotlinDataClass("dataclass.Person")
      .addProperty("name", "java.lang.String")
      .addProperty("age", "int");

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

  public R8KotlinDataClassTest(boolean allowAccessModification) {
    super(allowAccessModification);
  }

  @Parameters(name = "{0}")
  public static Collection<Object> data() {
    return ImmutableList.of(Boolean.TRUE, Boolean.FALSE);
  }

  @Test
  public void test_dataclass_gettersOnly() throws Exception {
    final String mainClassName = "dataclass.MainGettersOnlyKt";
    final MethodSignature testMethodSignature =
        new MethodSignature("testDataClassGetters", "void", Collections.emptyList());
    final String extraRules = keepClassMethod(mainClassName, testMethodSignature);
    runTest("dataclass", mainClassName, extraRules, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject dataClass = checkClassExists(dexInspector, TEST_DATA_CLASS.getClassName());

      // Getters should be removed after inlining, which is possible only if access is relaxed.
      final boolean areGetterPresent = !allowAccessModification;
      checkMethod(dataClass, NAME_GETTER_METHOD, areGetterPresent);
      checkMethod(dataClass, AGE_GETTER_METHOD, areGetterPresent);

      // No use of componentN functions.
      checkMethodIsAbsent(dataClass, COMPONENT1_METHOD);
      checkMethodIsAbsent(dataClass, COMPONENT2_METHOD);

      // No use of copy functions.
      checkMethodIsAbsent(dataClass, COPY_METHOD);
      checkMethodIsAbsent(dataClass, COPY_DEFAULT_METHOD);

      ClassSubject classSubject = checkClassExists(dexInspector, mainClassName);
      MethodSubject testMethod = checkMethodIsPresent(classSubject, testMethodSignature);
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
    final String mainClassName = "dataclass.MainComponentOnlyKt";
    final MethodSignature testMethodSignature =
        new MethodSignature("testAllDataClassComponentFunctions", "void", Collections.emptyList());
    final String extraRules = keepClassMethod(mainClassName, testMethodSignature);
    runTest("dataclass", mainClassName, extraRules, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject dataClass = checkClassExists(dexInspector, TEST_DATA_CLASS.getClassName());

      // ComponentN functions should be removed after inlining, which is possible only if access
      // is relaxed.
      final boolean areComponentMethodsPresent = !allowAccessModification;
      checkMethod(dataClass, COMPONENT1_METHOD, areComponentMethodsPresent);
      checkMethod(dataClass, COMPONENT2_METHOD, areComponentMethodsPresent);

      // No use of getter.
      checkMethodIsAbsent(dataClass, NAME_GETTER_METHOD);
      checkMethodIsAbsent(dataClass, AGE_GETTER_METHOD);

      // No use of copy functions.
      checkMethodIsAbsent(dataClass, COPY_METHOD);
      checkMethodIsAbsent(dataClass, COPY_DEFAULT_METHOD);

      ClassSubject classSubject = checkClassExists(dexInspector, mainClassName);
      MethodSubject testMethod = checkMethodIsPresent(classSubject, testMethodSignature);
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
    final String mainClassName = "dataclass.MainComponentPartialKt";
    final MethodSignature testMethodSignature =
        new MethodSignature("testSomeDataClassComponentFunctions", "void", Collections.emptyList());
    final String extraRules = keepClassMethod(mainClassName, testMethodSignature);
    runTest("dataclass", mainClassName, extraRules, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject dataClass = checkClassExists(dexInspector, TEST_DATA_CLASS.getClassName());

      boolean component2IsPresent = !allowAccessModification;
      checkMethod(dataClass, COMPONENT2_METHOD, component2IsPresent);

      // Function component1 is not used.
      checkMethodIsAbsent(dataClass, COMPONENT1_METHOD);

      // No use of getter.
      checkMethodIsAbsent(dataClass, NAME_GETTER_METHOD);
      checkMethodIsAbsent(dataClass, AGE_GETTER_METHOD);

      // No use of copy functions.
      checkMethodIsAbsent(dataClass, COPY_METHOD);
      checkMethodIsAbsent(dataClass, COPY_DEFAULT_METHOD);

      ClassSubject classSubject = checkClassExists(dexInspector, mainClassName);
      MethodSubject testMethod = checkMethodIsPresent(classSubject, testMethodSignature);
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
    final String mainClassName = "dataclass.MainComponentOnlyKt";
    final MethodSignature testMethodSignature =
        new MethodSignature("testDataClassCopy", "void", Collections.emptyList());
    final String extraRules = keepClassMethod(mainClassName, testMethodSignature);
    runTest("dataclass", mainClassName, extraRules, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject dataClass = checkClassExists(dexInspector, TEST_DATA_CLASS.getClassName());

      checkMethodIsAbsent(dataClass, COPY_METHOD);
      checkMethodIsAbsent(dataClass, COPY_DEFAULT_METHOD);
    });
  }

  @Test
  public void test_dataclass_copyDefaultIsRemovedIfNotUsed() throws Exception {
    final String mainClassName = "dataclass.MainCopyKt";
    final MethodSignature testMethodSignature =
        new MethodSignature("testDataClassCopyWithDefault", "void", Collections.emptyList());
    final String extraRules = keepClassMethod(mainClassName, testMethodSignature);
    runTest("dataclass", mainClassName, extraRules, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject dataClass = checkClassExists(dexInspector, TEST_DATA_CLASS.getClassName());

      checkMethodIsAbsent(dataClass, COPY_DEFAULT_METHOD);
    });
  }

}
