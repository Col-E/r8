// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AnnotationShakingBehaviorTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AnnotationShakingBehaviorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testKeepUnusedTypeInAnnotation()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(Factory.class, Main.class, C.class)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Factory.class)
        .addKeepAttributes("*Annotation*")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(inspector -> assertThat(inspector.clazz(C.class), isPresent()));
  }

  @Test
  public void testWillKeepAnnotatedProgramClass()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(Factory.class, MainWithMethodAnnotation.class, C.class)
        .addKeepMainRule(MainWithMethodAnnotation.class)
        .addKeepClassAndMembersRules(Factory.class)
        .addKeepRules(
            "-keepclassmembers,allowobfuscation,allowshrinking class "
                + MainWithMethodAnnotation.class.getTypeName()
                + "{void test();}")
        .addKeepAttributes("*Annotation*")
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MainWithMethodAnnotation.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(inspector -> assertThat(inspector.clazz(C.class), isPresent()));
  }

  @Test
  public void testGenericSignatureDoNotKeepType()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(MainWithGenericC.class, C.class)
        .addKeepMainRule(MainWithGenericC.class)
        .addKeepAttributes("Signature", "InnerClasses", "EnclosingMethod")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MainWithGenericC.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(C.class), not(isPresent()));
            });
  }

  @Test
  public void testDisappearsWhenMerging()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(Factory.class, MainWithNewB.class, A.class, B.class, C.class)
        .addKeepMainRule(MainWithNewB.class)
        .addKeepClassAndMembersRules(Factory.class)
        .addKeepAttributes("*Annotation*")
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MainWithNewB.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(C.class), not(isPresent()));
            });
  }

  @Retention(value = RetentionPolicy.RUNTIME)
  public @interface Factory {
    Class<?> ref() default Object.class;
  }

  public static class C {}

  @Factory(ref = C.class) // <-- we are explicitly keeping Main.
  public static class Main {

    public static void main(String[] args) {
      System.out.println("Hello World!");
    }
  }

  public static class MainWithMethodAnnotation {

    public static void main(String[] args) {
      test();
    }

    @Factory(ref = C.class) // <-- We are explicitly saying that test() should be kept.
    @NeverInline
    public static void test() {
      System.out.println("Hello World!");
    }
  }

  public static class MainWithGenericC {

    static List<C> cs = new ArrayList<>();

    public static void main(String[] args) {
      if (cs.size() == args.length) {
        System.out.println("Hello World!");
      }
    }
  }

  @Factory(ref = C.class)
  public static class A {}

  @NeverClassInline
  public static class B extends A {
    @NeverInline
    public void world() {
      System.out.println("Hello World!");
    }
  }

  public static class MainWithNewB {

    public static void main(String[] args) {
      new B().world();
    }
  }
}
