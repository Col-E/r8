// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b126592786;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B126592786 extends TestBase {

  private final Backend backend;
  private final boolean minify;

  @Parameterized.Parameters(name = "Backend: {0} minify: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(ToolHelper.getBackends(), BooleanUtils.values());
  }

  public B126592786(Backend backend, boolean minify) {
    this.backend = backend;
    this.minify = minify;
  }

  public void runTest(boolean genericTypeLive) throws Exception {
    Class<?> mainClass = genericTypeLive ? MainGenericTypeLive.class : MainGenericTypeNotLive.class;
    testForR8(backend)
        .minification(minify)
        .addProgramClasses(GetClassUtil.class, A.class, GenericType.class, mainClass, Marker.class)
        .addKeepMainRule(mainClass)
        .addKeepRules(
            "-keep class " + GetClassUtil.class.getTypeName() + " {",
            "  static java.lang.Class getClass(java.lang.Object);",
            "}",
            "-keepclassmembers @" + Marker.class.getTypeName() + " class * {",
            "  <fields>;",
            "}",
            "-keepattributes InnerClasses,EnclosingMethod,Signature ")
        .compile()
        .inspect(
            inspector -> {
              String genericTypeDescriptor = "Ljava/lang/Object;";
              if (genericTypeLive) {
                ClassSubject genericType = inspector.clazz(GenericType.class);
                assertThat(genericType, isPresentAndRenamed(minify));
                genericTypeDescriptor = genericType.getFinalDescriptor();
              }
              String expectedSignature = "Ljava/util/List<" + genericTypeDescriptor + ">;";
              FieldSubject list = inspector.clazz(A.class).uniqueFieldWithOriginalName("list");
              assertThat(list, isPresent());
              assertEquals(expectedSignature, list.getFinalSignatureAttribute());
            })
        .run(mainClass)
        .assertSuccess();
  }

  @Test
  public void testGenericClassNotLive() throws Exception {
    runTest(false);
  }

  @Test
  public void testGenericClassLive() throws Exception {
    runTest(true);
  }
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface Marker {
}

@Marker
class A {

  List<GenericType> list;
}

@Marker
class GenericType {

}

// GetClassUtil is used below to ensure that the types remain instantiated.
class GetClassUtil {

  public static Class<?> getClass(Object o) {
    return o.getClass();
  }
}

class MainGenericTypeNotLive {

  public static void main(String[] args) {
    System.out.println(GetClassUtil.getClass(new A()));
  }
}

class MainGenericTypeLive {

  public static void main(String[] args) {
    System.out.println(GetClassUtil.getClass(new A()));
    System.out.println(GetClassUtil.getClass(new GenericType()));
  }
}