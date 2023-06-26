// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithStringIdentifier extends RepackageTestBase {

  public final String EXPECTED = "Hello World!";

  public RepackageWithStringIdentifier(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(A.class, ACaller.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(A.class).removeInnerClasses().transform(),
            transformer(ACaller.class).removeInnerClasses().transform(),
            transformer(Main.class).removeInnerClasses().transform())
        .apply(this::configureRepackaging)
        .addKeepRules(
            "-identifiernamestring class "
                + Main.class.getTypeName()
                + " { java.lang.String name; }")
        .addKeepRules("-keepclassmembers,allowshrinking class **")
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForClasses()
        .enableNoAccessModificationAnnotationsForMembers()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> assertThat(A.class, isRepackaged(inspector)))
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessException.class);
  }

  @NeverClassInline
  @NoAccessModification
  static class A {

    @NoAccessModification
    A() {}

    @NeverInline
    public void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class ACaller {

    @NeverInline
    public static void callA() {
      new A().foo();
    }
  }

  public static class Main {

    private static String name;

    static {
      if (System.currentTimeMillis() > 0) {
        name = "com.android.tools.r8.repackage.RepackageWithStringIdentifier$A";
      }
    }

    public static void main(String[] args) throws Exception {
      Class.forName(name).getDeclaredConstructor().newInstance();
      ACaller.callA();
    }
  }
}
