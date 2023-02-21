// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignaturePrunedOuterRecursiveTest extends TestBase {

  private final TestParameters parameters;
  private final boolean isCompat;

  @Parameters(name = "{0}, isCompat: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public GenericSignaturePrunedOuterRecursiveTest(TestParameters parameters, boolean isCompat) {
    this.parameters = parameters;
    this.isCompat = isCompat;
  }

  @Test
  public void testR8() throws Exception {
    (isCompat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend()))
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Foo.class)
        .addKeepMainRule(Main.class)
        .addKeepAttributeSignature()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .setMinApi(parameters)
        .addOptionsModification(options -> options.horizontalClassMergerOptions().disable())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Bar::enclosingMethod")
        .inspect(this::checkSignatures);
  }

  public void checkSignatures(CodeInspector inspector) {
    checkSignature(
        inspector.clazz(Bar.class.getTypeName() + "$1"),
        "L" + binaryName(Foo.class) + "<Ljava/util/List<Ljava/lang/Object;>;>;");
  }

  private void checkSignature(ClassSubject classSubject, String expectedSignature) {
    assertThat(classSubject, isPresent());
    assertEquals(isCompat ? expectedSignature : null, classSubject.getFinalSignatureAttribute());
  }

  public abstract static class Foo<T> {

    void foo(T r) {
      System.out.println("Hello World");
    }
  }

  public static class Bar {

    public static <T extends List<T>> Foo<T> enclosingMethod() {
      return new Foo<T>() {
        @Override
        void foo(T r) {
          System.out.println("Bar::enclosingMethod");
        }
      };
    }

    public static void run() {
      enclosingMethod().foo(null);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Bar.run();
    }
  }
}
