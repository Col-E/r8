// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.annotations;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.utils.StringUtils;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DefaultAnnotationTest extends TestBase {

  static final String ANNO_NAME = typeName(MyAnnotation.class);
  static final String EXPECTED = StringUtils.lines("@" + ANNO_NAME + "(hello=Hello World!)");
  static final String EXPECTED_QUOTES =
      StringUtils.lines("@" + ANNO_NAME + "(hello=\"Hello World!\")");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public DefaultAnnotationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNoDefinition() throws Exception {
    runTest(b -> {});
  }

  @Test
  public void testOnLibrary() throws Exception {
    runTest(b -> b.addLibraryClassFileData(getDalvikAnnotationDefault()));
  }

  @Test
  public void testOnProgram() throws Exception {
    runTest(b -> b.addProgramClassFileData(getDalvikAnnotationDefault()));
  }

  private void runTest(ThrowableConsumer<R8FullTestBuilder> modification) throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(MyAnnotation.class, MyAnnotatedClass.class, TestClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .apply(modification)
        .addKeepAllAttributes()
        .addKeepRules("-keep class * { *; }")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpected());
  }

  private String getExpected() {
    if (parameters.isCfRuntime() && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK9)) {
      return EXPECTED_QUOTES;
    }
    return EXPECTED;
  }

  private static byte[] getDalvikAnnotationDefault() throws Exception {
    return transformer(WillBeDalvikAnnotationAnnotationDefault.class)
        .setClassDescriptor("Ldalvik/annotation/AnnotationDefault;")
        .transform();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.ANNOTATION_TYPE)
  @interface WillBeDalvikAnnotationAnnotationDefault {}

  @Retention(RetentionPolicy.RUNTIME)
  @interface MyAnnotation {
    String hello() default "Hello World!";
  }

  @MyAnnotation
  static class MyAnnotatedClass {}

  static class TestClass {

    public static void main(String[] args) {
      for (Annotation annotation : MyAnnotatedClass.class.getAnnotations()) {
        System.out.println(annotation.toString());
      }
    }
  }
}
