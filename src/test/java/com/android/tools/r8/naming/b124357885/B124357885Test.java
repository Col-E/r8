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

import com.android.tools.r8.KeepUnusedReturnValue;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.BooleanUtils;
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
    assertEquals("()" + inspector.clazz(FooImpl.class).getFinalDescriptor(), signature);
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult compileResult =
        testForR8Compat(parameters.getBackend())
            .addProgramClasses(Main.class, Foo.class, FooImpl.class)
            .addKeepMainRule(Main.class)
            .addKeepAttributes(
                ProguardKeepAttributes.SIGNATURE,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .applyIf(
                minification,
                testBuilder ->
                    testBuilder.addKeepRules(
                        "-keep,allowoptimization,allowshrinking class "
                            + Main.class.getTypeName()
                            + " { *** test(); }"))
            .enableInliningAnnotations()
            .enableKeepUnusedReturnValueAnnotations()
            .minification(minification)
            .setMinApi(parameters)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject mainClass = inspector.clazz(Main.class);
                  assertThat(mainClass, isPresentAndNotRenamed());
                  assertThat(inspector.clazz(Foo.class), not(isPresent()));
                  assertThat(inspector.clazz(FooImpl.class), isPresentAndRenamed(minification));
                  // TODO(124477502): Using uniqueMethodWithName("test") does not work.
                  MethodSubject testMethod =
                      mainClass.uniqueMethodThatMatches(
                          method ->
                              method.isStatic()
                                  && !method.getMethod().getName().toString().equals("main"));
                  assertThat(testMethod, isPresent());
                  checkSignature(
                      inspector, testMethod.asFoundMethodSubject().getFinalSignatureAttribute());
                });

    String fooImplFinalName = compileResult.inspector().clazz(FooImpl.class).getFinalName();

    compileResult
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(
            StringUtils.lines(fooImplFinalName, fooImplFinalName, "Hello world!"));
  }

  public static class Main {
    public static void main(String... args) throws Exception {
      String methodName = System.currentTimeMillis() >= 0 ? "test" : null;
      Method method = Main.class.getDeclaredMethod(methodName);

      try {
        ParameterizedType type = (ParameterizedType) method.getGenericReturnType();
        Class<?> rawType = (Class<?>) type.getRawType();
        System.out.println(rawType.getName());
      } catch (ClassCastException e) {
        System.out.println(((Class<?>) method.getGenericReturnType()).getName());
      }

      // Convince R8 we only use subtypes to get class merging of Foo into FooImpl.
      Foo<String> foo = new FooImpl<>();
      System.out.println(foo.getClass().getCanonicalName());

      // Ensure test() remains in output.
      test();
    }

    @NeverInline
    @KeepUnusedReturnValue
    static Foo<String> test() {
      System.out.println("Hello world!");
      return new FooImpl<>();
    }
  }

  interface Foo<T> {}

  public static class FooImpl<T> implements Foo<T> {}
}
