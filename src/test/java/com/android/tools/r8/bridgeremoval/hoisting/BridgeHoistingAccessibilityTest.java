// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bridgeremoval.hoisting;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.bridgeremoval.hoisting.testclasses.BridgeHoistingAccessibilityTestClasses;
import com.android.tools.r8.bridgeremoval.hoisting.testclasses.BridgeHoistingAccessibilityTestClasses.AWithRangedInvoke;
import com.android.tools.r8.bridgeremoval.hoisting.testclasses.BridgeHoistingAccessibilityTestClasses.User;
import com.android.tools.r8.bridgeremoval.hoisting.testclasses.BridgeHoistingAccessibilityTestClasses.UserWithRangedInvoke;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BridgeHoistingAccessibilityTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public BridgeHoistingAccessibilityTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addInnerClasses(BridgeHoistingAccessibilityTestClasses.class)
        .addProgramClassFileData(
            transformer(B.class)
                .setBridge(B.class.getDeclaredMethod("bridgeB", Object.class))
                .transform(),
            transformer(C.class)
                .setBridge(C.class.getDeclaredMethod("bridgeC", Object.class))
                .transform(),
            transformer(BWithRangedInvoke.class)
                .setBridge(
                    BWithRangedInvoke.class.getDeclaredMethod(
                        "bridgeB",
                        Object.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class))
                .transform(),
            transformer(CWithRangedInvoke.class)
                .setBridge(
                    CWithRangedInvoke.class.getDeclaredMethod(
                        "bridgeC",
                        Object.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class))
                .transform())
        .addKeepMainRule(TestClass.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with argument changes.
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!", "Hello 12345 world! 12345");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(BridgeHoistingAccessibilityTestClasses.A.class);
    assertThat(aClassSubject, isPresent());
    assertThat(aClassSubject.uniqueMethodWithOriginalName("m"), isPresent());
    assertThat(aClassSubject.uniqueMethodWithOriginalName("bridgeC"), isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    assertThat(bClassSubject.uniqueMethodWithOriginalName("bridgeB"), isPresent());

    ClassSubject cClassSubject = inspector.clazz(C.class);
    assertThat(cClassSubject, isPresent());

    ClassSubject axClassSubject = inspector.clazz(AWithRangedInvoke.class);
    assertThat(axClassSubject, isPresent());
    assertThat(axClassSubject.uniqueMethodWithOriginalName("m"), isPresent());
    assertThat(axClassSubject.uniqueMethodWithOriginalName("bridgeC"), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      C instance = new C();
      System.out.print(instance.bridgeB("Hello"));
      System.out.println(User.invokeBridgeC(instance));

      CWithRangedInvoke instanceWithRangedInvoke = new CWithRangedInvoke();
      System.out.print(instanceWithRangedInvoke.bridgeB("Hello ", 1, 2, 3, 4, 5));
      System.out.println(UserWithRangedInvoke.invokeBridgeC(instanceWithRangedInvoke));
    }
  }

  @NoVerticalClassMerging
  static class B extends BridgeHoistingAccessibilityTestClasses.A {

    // This bridge cannot be hoisted to A, since it would then become inaccessible to the call site
    // in TestClass.main().
    @KeepConstantArguments
    @NeverInline
    @NoAccessModification
    @NoMethodStaticizing
    /*bridge*/ String bridgeB(Object o) {
      return (String) m((String) o);
    }
  }

  @NeverClassInline
  public static class C extends B {

    // This bridge is invoked from another package. However, this does not prevent us from hoisting
    // the bridge to B, although B is not public, since users from outside this package can still
    // access bridgeC() via class C. From B, the bridge can be hoisted again to A.
    @KeepConstantArguments
    @NeverInline
    @NoMethodStaticizing
    public /*bridge*/ String bridgeC(Object o) {
      return (String) m((String) o);
    }
  }

  @NoVerticalClassMerging
  static class BWithRangedInvoke extends AWithRangedInvoke {

    // This bridge cannot be hoisted to A, since it would then become inaccessible to the call site
    // in TestClass.main().
    @KeepConstantArguments
    @NeverInline
    @NoAccessModification
    @NoMethodStaticizing
    /*bridge*/ String bridgeB(Object o, int a, int b, int c, int d, int e) {
      return (String) m((String) o, a, b, c, d, e);
    }
  }

  @NeverClassInline
  public static class CWithRangedInvoke extends BWithRangedInvoke {

    // This bridge is invoked from another package. However, this does not prevent us from hoisting
    // the bridge to B, although B is not public, since users from outside this package can still
    // access bridgeC() via class C. From B, the bridge can be hoisted again to A.
    @KeepConstantArguments
    @NeverInline
    @NoMethodStaticizing
    public /*bridge*/ String bridgeC(Object o, int a, int b, int c, int d, int e) {
      return (String) m((String) o, a, b, c, d, e);
    }
  }
}
