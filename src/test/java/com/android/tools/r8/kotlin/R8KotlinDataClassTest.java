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
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
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
    final String mainClassName = "dataclass.MainGettersOnly";
    buildAndInspect("dataclass", mainClassName, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject dataClass = checkClassExists(dexInspector, TEST_DATA_CLASS.getClassName());

      // Getters should be removed after inlining, which is possible only if access is relaxed.
      final boolean areGetterPresent = !allowAccessModification;

      Map<MethodSignature, Boolean> presenceMap = ImmutableMap.<MethodSignature, Boolean>builder()
          .put(NAME_GETTER_METHOD, areGetterPresent)
          .put(AGE_GETTER_METHOD, areGetterPresent)
          // ComponentN and copy methods are not used.
          .put(COMPONENT1_METHOD, false)
          .put(COMPONENT2_METHOD, false)
          .put(COPY_METHOD, false)
          .put(COPY_DEFAULT_METHOD, false)
          .build();
      checkMethodsPresence(dataClass, presenceMap);

      ClassSubject classSubject = checkClassExists(dexInspector, mainClassName);
      MethodSubject testMethod = checkMethodIsPresent(classSubject, "testMethod", "void",
          Collections.emptyList());
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
    final String mainClassName = "dataclass.MainComponentOnly";
    buildAndInspect("dataclass", mainClassName, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject dataClass = checkClassExists(dexInspector, TEST_DATA_CLASS.getClassName());

      // ComponentN functions should be removed after inlining, which is possible only if access
      // is relaxed.
      final boolean areComponentMethodsPresent = !allowAccessModification;

      Map<MethodSignature, Boolean> presenceMap = ImmutableMap.<MethodSignature, Boolean>builder()
          .put(NAME_GETTER_METHOD, false)
          .put(AGE_GETTER_METHOD, false)
          // ComponentN and copy methods are not used.
          .put(COMPONENT1_METHOD, areComponentMethodsPresent)
          .put(COMPONENT2_METHOD, areComponentMethodsPresent)
          .put(COPY_METHOD, false)
          .put(COPY_DEFAULT_METHOD, false)
          .build();
      checkMethodsPresence(dataClass, presenceMap);

      ClassSubject classSubject = checkClassExists(dexInspector, mainClassName);
      MethodSubject testMethod = checkMethodIsPresent(classSubject, "testMethod", "void",
          Collections.emptyList());
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
    final String mainClassName = "dataclass.MainComponentPartial";
    buildAndInspect("dataclass", mainClassName, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject dataClass = checkClassExists(dexInspector, TEST_DATA_CLASS.getClassName());

      boolean component2IsPresent = !allowAccessModification;

      Map<MethodSignature, Boolean> presenceMap = ImmutableMap.<MethodSignature, Boolean>builder()
          .put(NAME_GETTER_METHOD, false)
          .put(AGE_GETTER_METHOD, false)
          // ComponentN and copy methods are not used.
          .put(COMPONENT1_METHOD, false)
          .put(COMPONENT2_METHOD, component2IsPresent)
          .put(COPY_METHOD, false)
          .put(COPY_DEFAULT_METHOD, false)
          .build();
      checkMethodsPresence(dataClass, presenceMap);

      ClassSubject classSubject = checkClassExists(dexInspector, mainClassName);
      MethodSubject testMethod = checkMethodIsPresent(classSubject, "testMethod", "void",
          Collections.emptyList());
      DexCode dexCode = getDexCode(testMethod);
      if (allowAccessModification) {
        checkMethodIsNeverInvoked(dexCode, COMPONENT2_METHOD);
      } else {
        checkMethodIsInvokedAtLeastOnce(dexCode, COMPONENT2_METHOD);
      }
    });
  }

  @Test
  public void test_dataclass_copy() throws Exception {
    final String mainClassName = "dataclass.MainCopy";
    buildAndInspect("dataclass", mainClassName, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject dataClass = checkClassExists(dexInspector, TEST_DATA_CLASS.getClassName());

      // Copy method is small enough that it is always inlined.
      final boolean copyMethodIsPresent = false;

      Map<MethodSignature, Boolean> presenceMap = ImmutableMap.<MethodSignature, Boolean>builder()
          .put(COPY_METHOD, copyMethodIsPresent)
          .put(COPY_DEFAULT_METHOD, false)
          .build();
      checkMethodsPresence(dataClass, presenceMap);
    });
  }

  @Test
  public void test_dataclass_copyDefault() throws Exception {
    final String mainClassName = "dataclass.MainCopyWithDefault";
    buildAndInspect("dataclass", mainClassName, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject dataClass = checkClassExists(dexInspector, TEST_DATA_CLASS.getClassName());

      // Copy$default method is inlined if access is relaxed.
      final boolean copyDefaultMethodIsPresent = !allowAccessModification;

      // copy$default is a wrapper around copy to deal with default values. If it's inlined thus
      // copy is inlined as well.
      final boolean copyMethodIsPresent = copyDefaultMethodIsPresent;

      Map<MethodSignature, Boolean> presenceMap = ImmutableMap.<MethodSignature, Boolean>builder()
          .put(COPY_DEFAULT_METHOD, copyDefaultMethodIsPresent)
          .put(COPY_METHOD, copyMethodIsPresent)
          .build();
      checkMethodsPresence(dataClass, presenceMap);

      if (copyDefaultMethodIsPresent) {
        ClassSubject classSubject = checkClassExists(dexInspector, mainClassName);
        MethodSubject testMethod = checkMethodIsPresent(classSubject, "testMethod", "void",
            Collections.emptyList());
        DexCode dexCode = getDexCode(testMethod);
        checkMethodIsInvokedAtLeastOnce(dexCode, COPY_DEFAULT_METHOD);
      }
      if (copyMethodIsPresent) {
        MethodSubject testMethod = checkMethod(dataClass, COPY_DEFAULT_METHOD, true);
        DexCode dexCode = getDexCode(testMethod);
        checkMethodIsInvokedAtLeastOnce(dexCode, COPY_METHOD);
      }
    });
  }

}
