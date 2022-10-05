// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.attributes;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepAttributesDotsTest extends TestBase {

  private final String keepAttributes;

  @Parameterized.Parameters(name = "-keepattributes {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        new String[] {".", "...", "XYZ,..", "XYZ,..,A.B"});
  }

  public KeepAttributesDotsTest(TestParameters parameters, String keepAttributes) {
    parameters.assertNoneRuntime();
    this.keepAttributes = keepAttributes;
  }

  @Test
  public void testProguard() throws ExecutionException, CompilationFailedException, IOException {
    testForProguard()
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .addKeepAttributes(keepAttributes)
        .addDontWarn(KeepAttributesDotsTest.class)
        .run(TestRuntime.getCheckedInJdk9(), Main.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(Backend.CF)
        .addInnerClasses(getClass())
        .addKeepAttributes(keepAttributes)
        .addKeepAllClassesRule()
        .run(TestRuntime.getCheckedInJdk9(), Main.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Main.class);
    assertTrue(clazz.getDexProgramClass().annotations().isEmpty());
    MethodSubject main = clazz.uniqueMethodWithOriginalName("main");
    assertTrue(main.getMethod().annotations().isEmpty());
    FieldSubject field = clazz.uniqueFieldWithOriginalName("field");
    assertTrue(field.getField().annotations().isEmpty());
    assertTrue(
        clazz.getDexProgramClass().sourceFile == null
            || clazz.getDexProgramClass().sourceFile.toString().equals("SourceFile"));
    assertNull(main.getLineNumberTable());
    assertTrue(main.getLocalVariableTable().isEmpty());
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD})
  public @interface MethodRuntimeAnnotation {}

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.METHOD})
  public @interface MethodCompileTimeAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface ClassRuntimeAnnotation {}

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.TYPE})
  public @interface ClassCompileTimeAnnotation {}

  @ClassCompileTimeAnnotation
  @ClassRuntimeAnnotation
  public static class Main {

    public static class Inner<T> {}

    public Inner<Boolean> field;

    @MethodCompileTimeAnnotation
    @MethodRuntimeAnnotation
    public static void main(String[] args) {
      System.out.println("Hello World!");
    }
  }
}
