// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.genericsignature.GenericSignatureEnclosingTest.Bar.Inner;
import com.android.tools.r8.graph.genericsignature.GenericSignatureEnclosingTest.Bar.SubInner;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureEnclosingTest extends TestBase {

  private final TestParameters parameters;
  private final boolean isCompat;
  private final String objectDescriptor = "Ljava/lang/Object;";

  @Parameters(name = "{0}, isCompat: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public GenericSignatureEnclosingTest(TestParameters parameters, boolean isCompat) {
    this.parameters = parameters;
    this.isCompat = isCompat;
  }

  @Test()
  public void testR8() throws Exception {
    (isCompat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend()))
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Foo.class, Bar.class, Bar.Inner.class, Bar.SubInner.class)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(Foo.class)
        .addKeepAttributeSignature()
        .setMinApi(parameters)
        .addOptionsModification(
            options -> {
              options.horizontalClassMergerOptions().disable();
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "Bar::enclosingMethod", "Hello World", "Bar::enclosingMethod2", "Hello World")
        .inspect(
            inspector -> {
              inspectEnclosingClass(inspector, "$1", descriptor(Main.class));
              inspectEnclosingClass(inspector, "$2", objectDescriptor);

              ClassSubject inner = inspector.clazz(Inner.class);
              assertThat(inner, isPresent());
              assertTrue(inner.getDexProgramClass().getInnerClasses().isEmpty());
              assertEquals(
                  "<T:" + objectDescriptor + ">" + objectDescriptor,
                  inner.getFinalSignatureAttribute());

              ClassSubject subInner = inspector.clazz(SubInner.class);
              assertThat(subInner, isPresent());
              assertTrue(subInner.getDexProgramClass().getInnerClasses().isEmpty());
              assertEquals(
                  "L" + binaryName(Bar.Inner.class) + "<Ljava/lang/String;>;",
                  subInner.getFinalSignatureAttribute());
            });
  }

  private void inspectEnclosingClass(CodeInspector inspector, String suffix, String secondArg) {
    ClassSubject enclosing = inspector.clazz(Bar.class.getTypeName() + suffix);
    assertThat(enclosing, isPresent());
    assertNull(enclosing.getDexProgramClass().getEnclosingMethodAttribute());
    assertEquals(
        isCompat ? "L" + binaryName(Foo.class) + "<Ljava/lang/Object;" + secondArg + ">;" : null,
        enclosing.getFinalSignatureAttribute());
  }

  public abstract static class Foo<T, R> {

    R foo(T r) {
      System.out.println("Hello World");
      return null;
    }
  }

  public static class Bar<S> {

    public class Inner<T> {}

    public class SubInner extends Bar<Integer>.Inner<String> {}

    public static <T, R extends Main> Foo<T, R> enclosingMethod() {
      return new Foo<T, R>() {
        @Override
        R foo(T r) {
          System.out.println("Bar::enclosingMethod");
          return super.foo(r);
        }
      };
    }

    public static <T, R> Foo<T, R> enclosingMethod2() {
      return new Foo<T, R>() {
        @Override
        R foo(T r) {
          System.out.println("Bar::enclosingMethod2");
          return super.foo(r);
        }
      };
    }

    public static void run() {
      enclosingMethod().foo(null);
      enclosingMethod2().foo(null);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Bar.run();
    }
  }
}
