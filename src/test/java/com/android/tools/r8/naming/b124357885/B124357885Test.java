// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b124357885;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B124357885Test extends TestBase {

  private final TestParameters parameters;
  private final boolean minification;

  @Parameters(name = "{0}, minification: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public B124357885Test(TestParameters parameters, boolean minification) {
    this.parameters = parameters;
    this.minification = minification;
  }

  private void checkSignature(CodeInspector inspector, String signature) {
    String fooImplFinalDescriptor =
        DescriptorUtils.javaTypeToDescriptor(inspector.clazz(FooImpl.class).getFinalName());
    StringBuilder expected =
        new StringBuilder()
            .append("()")
            // Remove the final ; from the descriptor to add the generic type.
            .append(fooImplFinalDescriptor.substring(0, fooImplFinalDescriptor.length() - 1))
            .append("<Ljava/lang/String;>")
            // Add the ; after the generic type.
            .append(";");
    assertEquals(expected.toString(), signature);
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult compileResult =
        testForR8Compat(parameters.getBackend())
            .addProgramClasses(Main.class, Service.class, Foo.class, FooImpl.class)
            .addKeepMainRule(Main.class)
            .addKeepAttributes(
                ProguardKeepAttributes.SIGNATURE,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .minification(minification)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(
                inspector -> {
                  assertThat(inspector.clazz(Main.class), isPresentAndNotRenamed());
                  assertThat(inspector.clazz(Foo.class), not(isPresent()));
                  assertThat(inspector.clazz(FooImpl.class), isPresentAndRenamed(minification));
                  ClassSubject serviceClass = inspector.clazz(Service.class);
                  assertThat(serviceClass, isPresentAndRenamed(minification));
                  // TODO(124477502): Using uniqueMethodWithName("fooList") does not work.
                  assertEquals(1, serviceClass.allMethods().size());
                  MethodSubject fooList = serviceClass.allMethods().get(0);
                  assertThat(fooList, isPresent());
                  checkSignature(
                      inspector, fooList.asFoundMethodSubject().getFinalSignatureAttribute());
                });

    String fooImplFinalName = compileResult.inspector().clazz(FooImpl.class).getFinalName();

    compileResult
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(StringUtils.lines(fooImplFinalName, fooImplFinalName));
  }

  public static class Main {
    public static void main(String... args) throws Exception {
      Method method = Service.class.getMethod("fooList");
      ParameterizedType type = (ParameterizedType) method.getGenericReturnType();
      Class<?> rawType = (Class<?>) type.getRawType();
      System.out.println(rawType.getName());

      // Convince R8 we only use subtypes to get class merging of Foo into FooImpl.
      Foo<String> foo = new FooImpl<>();
      System.out.println(foo.getClass().getCanonicalName());
    }
  }

  interface Service {
    Foo<String> fooList();
  }

  interface Foo<T> {}

  public static class FooImpl<T> implements Foo<T> {}
}
