// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ApplyMappingNonMemberClassTest extends TestBase {

  static class Enclosing {
    @NeverInline
    void foo() {
      Runnable r = new Runnable() {
        @Override
        public void run() {
          System.out.println("Anonymous run");
        }
      };

      class Local {
        Runnable r;
        Local(Runnable r) {
          this.r = r;
        }

        void run() {
          r.run();
        }
      }
      Local l = new Local(r);
      l.run();
    }
  }

  static class C {
    public static void main(String... args) {
      new Enclosing().foo();
    }
  }

  private static final String pgMap = StringUtils.lines(
      Enclosing.class.getName() + " -> a:",
      Enclosing.class.getName() + "$1 -> a$1:",
      Enclosing.class.getName() + "$1Local -> a$a:",
      "  void run() -> a",
      C.class.getName() + " -> Main:"
  );

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private TestParameters parameters;

  public ApplyMappingNonMemberClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(Enclosing.class)
        .addProgramClasses(C.class)
        .addKeepRules(
            "-keepclassmembers class "
                + C.class.getName()
                + "{ public static void main(java.lang.String[]); }")
        .addKeepClassRulesWithAllowObfuscation(C.class)
        .addKeepAttributes("InnerClasses", "EnclosingMethod")
        .allowAccessModification()
        .enableInliningAnnotations()
        .addApplyMapping(pgMap)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), "Main")
        .assertSuccessWithOutput(StringUtils.lines("Anonymous run"));
  }

  @Test
  public void testR8Compat() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addProgramClassesAndInnerClasses(Enclosing.class)
        .addProgramClasses(C.class)
        .addKeepRules(
            "-keepclassmembers class "
                + C.class.getName()
                + "{ public static void main(java.lang.String[]); }")
        .addKeepClassRulesWithAllowObfuscation(C.class)
        .addKeepAttributes("InnerClasses", "EnclosingMethod")
        .allowAccessModification()
        .enableInliningAnnotations()
        .addApplyMapping(pgMap)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), "Main")
        .assertSuccessWithOutput(StringUtils.lines("Anonymous run"));
  }
}
