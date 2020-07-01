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
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B124357885Test extends TestBase {
  public final boolean minification;

  @Parameterized.Parameters(name = "Minification: {0}")
  public static Boolean[] data() {
    return BooleanUtils.values();
  }

  public B124357885Test(boolean minification) {
    this.minification = minification;
  }

  private void checkSignatureAnnotation(CodeInspector inspector, AnnotationSubject signature) {
    DexAnnotationElement[] elements = signature.getAnnotation().elements;
    assertEquals(1, elements.length);
    assertEquals("value", elements[0].name.toString());
    assertTrue(elements[0].value.isDexValueArray());
    DexValueArray array = elements[0].value.asDexValueArray();
    StringBuilder builder = new StringBuilder();
    for (DexValue value : array.getValues()) {
      assertTrue(value.isDexValueString());
      builder.append(value.asDexValueString().value);
    }
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
    assertEquals(expected.toString(), builder.toString());
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(Backend.DEX)
            .addProgramClasses(Main.class, Service.class, Foo.class, FooImpl.class)
            .addKeepMainRule(Main.class)
            .addKeepRules("-keepattributes Signature,InnerClasses,EnclosingMethod")
            .minification(minification)
            .compile()
            .inspect(
                inspector -> {
                  assertThat(inspector.clazz(Main.class), isPresentAndNotRenamed());
                  assertThat(inspector.clazz(Service.class), isPresentAndRenamed(minification));
                  assertThat(inspector.clazz(Foo.class), not(isPresent()));
                  assertThat(inspector.clazz(FooImpl.class), isPresentAndRenamed(minification));
                  // TODO(124477502): Using uniqueMethodWithName("fooList") does not work.
                  assertEquals(1, inspector.clazz(Service.class).allMethods().size());
                  MethodSubject fooList = inspector.clazz(Service.class).allMethods().get(0);
                  AnnotationSubject signature = fooList.annotation("dalvik.annotation.Signature");
                  checkSignatureAnnotation(inspector, signature);
                });

        String fooImplFinalName = compileResult.inspector().clazz(FooImpl.class).getFinalName();

        compileResult
            .run(Main.class)
            .assertSuccessWithOutput(StringUtils.lines(fooImplFinalName, fooImplFinalName));
  }
}

class Main {
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

class FooImpl<T> implements Foo<T> {}
