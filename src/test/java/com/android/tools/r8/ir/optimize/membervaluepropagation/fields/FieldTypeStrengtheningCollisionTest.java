// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation.fields;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.FieldPredicate;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldTypeStrengtheningCollisionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class)
        .addProgramClassFileData(getTransformedMain())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class " + Main.class.getTypeName() + " { " + A.class.getTypeName() + " f; }")
        .enableInliningAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              FieldSubject keptFieldSubject = mainClassSubject.uniqueFieldWithFinalName("f");
              assertEquals(
                  aClassSubject.getFinalName(),
                  keptFieldSubject.getField().getType().getTypeName());

              FieldSubject optimizedFieldSubject = mainClassSubject.uniqueFieldWithFinalName("f$1");
              assertEquals(
                  aClassSubject.getFinalName(),
                  optimizedFieldSubject.getField().getType().getTypeName());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A");
  }

  private static byte[] getTransformedMain() throws IOException {
    return transformer(Main.class).renameField(FieldPredicate.onName("f1"), "f").transform();
  }

  static class Main {

    // @Keep
    static A f1; // renamed to f

    static Object f;

    public static void main(String[] args) {
      setField();
      printField();
    }

    @NeverInline
    static void setField() {
      f = new A();
    }

    @NeverInline
    static void printField() {
      System.out.println(f);
    }
  }

  public static class A {

    @Override
    public String toString() {
      return "A";
    }
  }
}
