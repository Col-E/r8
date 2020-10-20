// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.attributes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassSignaturesTest extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED = "Hello World!";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassSignaturesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(A.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, Main.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepAttributes(
            ProguardKeepAttributes.SIGNATURE,
            ProguardKeepAttributes.INNER_CLASSES,
            ProguardKeepAttributes.ENCLOSING_METHOD)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClass = inspector.clazz(A.class);
    assertThat(aClass, isPresent());
    assertEquals(
        "Ljava/lang/Object;Ljava/lang/Iterable<"
            + "Lcom/android/tools/r8/shaking/attributes/ClassSignaturesTest$Main;>;",
        aClass.getFinalSignatureAttribute());
  }

  @NeverClassInline
  public static class A implements Iterable<Main> {

    @Override
    @NeverInline
    public Iterator<Main> iterator() {
      throw new RuntimeException("FooBar");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      try {
        new A().iterator();
      } catch (RuntimeException e) {
        if (!e.getMessage().contains("FooBar")) {
          throw e;
        }
        System.out.println("Hello World!");
      }
    }
  }
}
