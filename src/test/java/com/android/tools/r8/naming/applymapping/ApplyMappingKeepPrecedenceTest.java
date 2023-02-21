// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingKeepPrecedenceTest extends TestBase {

  @NeverClassInline
  public static class A { // Should be kept with all members without renaming.

    @NeverInline
    public static void print() {
      System.out.println("What is the answer to life the universe and everything?");
    }
  }

  @NeverClassInline
  public static class B { // Should be kept with all members without renaming.

    @NeverPropagateValue int foo = 4;

    @NeverInline
    int bar() {
      if (System.currentTimeMillis() > 0) {
        return 2;
      } else {
        return 1;
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.print();
      B b = new B();
      System.out.println(b.foo + "" + b.bar());
    }
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApplyMappingKeepPrecedenceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNaming() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(ApplyMappingKeepPrecedenceTest.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addApplyMapping(
            A.class.getTypeName()
                + " -> "
                + ApplyMappingKeepPrecedenceTest.class.getTypeName()
                + "$a:\n"
                + B.class.getTypeName()
                + " -> "
                + ApplyMappingKeepPrecedenceTest.class.getTypeName()
                + "$b:\n"
                + "  int foo -> x\n"
                + "  int bar() -> y\n")
        .addKeepClassRules(A.class)
        .addKeepRules("-keepclassmembernames class " + B.class.getTypeName() + " { *; }")
        .addKeepMainRule(Main.class)
        .enableMemberValuePropagationAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "What is the answer to life the universe and everything?", "42")
        .inspect(
            codeInspector -> {
              ClassSubject clazzA = codeInspector.clazz(A.class);
              assertTrue(clazzA.isPresent());
              assertFalse(clazzA.isRenamed());
              ClassSubject clazzB = codeInspector.clazz(B.class);
              assertTrue(clazzB.isPresent());
              assertTrue(clazzB.isRenamed());
              FieldSubject foo = clazzB.uniqueFieldWithOriginalName("foo");
              assertTrue(foo.isPresent());
              assertFalse(foo.isRenamed());
              MethodSubject bar = clazzB.uniqueMethodWithOriginalName("bar");
              assertTrue(bar.isPresent());
              assertFalse(bar.isRenamed());
            });
  }
}
