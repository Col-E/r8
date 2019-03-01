// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b126592786;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
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
    return buildParameters(Backend.values(), BooleanUtils.values());
  }

  public B126592786(Backend backend, boolean minify) {
    this.backend = backend;
    this.minify = minify;
  }

  public void runTest(boolean genericTypeLive) throws Exception {
    Class<?> mainClass = genericTypeLive ? MainGenericTypeLive.class : MainGenericTypeNotLive.class;
    testForR8(backend)
        .minification(minify)
        .addProgramClasses(A.class, GenericType.class, mainClass)
        .addKeepMainRule(mainClass)
        .addKeepRules(
            "-keepclassmembers @" + Marker.class.getTypeName() + " class * {",
            "  <fields>;",
            "}",
            "-keepattributes InnerClasses,EnclosingMethod,Signature ")
        .compile()
        .inspect(inspector -> {
            String genericTypeDescriptor = "Ljava/lang/Object;";
            if (genericTypeLive) {
              ClassSubject genericType = inspector.clazz(GenericType.class);
              assertThat(genericType, isRenamed(minify));
              genericTypeDescriptor = genericType.getFinalDescriptor();
            }
            String expectedSignature = "Ljava/util/List<" + genericTypeDescriptor + ">;";
            FieldSubject list = inspector.clazz(A.class).uniqueFieldWithName("list");
            assertThat(list, isPresent());
            assertThat(list.getSignatureAnnotation(), isPresent());
            assertEquals(expectedSignature, list.getSignatureAnnotationValue());
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

class MainGenericTypeNotLive {

  public static void main(String[] args) {
    System.out.println(A.class);
  }
}

class MainGenericTypeLive {

  public static void main(String[] args) {
    System.out.println(A.class);
    System.out.println(GenericType.class);
  }
}