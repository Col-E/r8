// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithInitClassTest extends RepackageTestBase {

  public RepackageWithInitClassTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addClassObfuscationDictionary("a")
        .addKeepMainRule(TestClass.class)
        .apply(this::configureRepackaging)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject repackagedClassSubject = inspector.clazz(StaticMemberValuePropagation.class);
    assertThat(repackagedClassSubject, isPresent());

    // Verify that a $r8$clinit field was synthesized.
    String clinitFieldName = inspector.getFactory().objectMembers.clinitField.name.toSourceString();
    assertThat(repackagedClassSubject.uniqueFieldWithName(clinitFieldName), isPresent());
    assertThat(repackagedClassSubject.uniqueFieldWithName("GREETING"), not(isPresent()));

    // Verify that the class was repackaged.
    assertThat(StaticMemberValuePropagation.class, isRepackaged(inspector));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(StaticMemberValuePropagation.GREETING);
    }
  }

  public static class StaticMemberValuePropagation {

    public static String GREETING = " world!";

    static {
      System.out.print("Hello");
    }
  }
}
