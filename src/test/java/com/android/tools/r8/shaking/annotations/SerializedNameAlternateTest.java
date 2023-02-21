// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ProguardTestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SerializedNameAlternateTest extends TestBase {

  public static final String EXPECTED_DEFAULT_VALUE_ANNOTATION = "Kept DefaultValue Annotation";
  public static final String EXPECTED_SERIALIZED_ANNOTATION = "Kept SerializedName Annotation";

  @Retention(RetentionPolicy.RUNTIME)
  @interface SerializedName {
    String value();

    String[] alternate() default {};
  }

  public static class Foo {

    @SerializedName(
        value = "bar",
        alternate = {"foo"})
    public String bar = "bar";
  }

  public static class Main {

    public static void main(String[] args) throws NoSuchFieldException {
      Annotation[] annotations = SerializedName.class.getAnnotations();
      if (annotations.length > 0) {
        System.out.println("Kept DefaultValue Annotation");
      }
      if (Foo.class.getField("bar").getAnnotations().length == 1) {
        System.out.println("Kept SerializedName Annotation");
      }
    }
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SerializedNameAlternateTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testKeepSerializedName()
      throws IOException, CompilationFailedException, ExecutionException {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addInnerClasses(SerializedNameAlternateTest.class)
            .addKeepAttributes("*Annotation*")
            .addKeepMainRule(Main.class)
            .addKeepClassRules(Foo.class)
            .addKeepClassRules(SerializedName.class)
            .addKeepRules(
                // Non-compat mode only retains annotations for items matched by a -keep rule.
                "-keepclassmembers,allowobfuscation,allowshrinking class "
                    + Foo.class.getTypeName()
                    + " {",
                "  @" + SerializedName.class.getTypeName() + " <fields>;",
                "}")
            .setMinApi(parameters)
            .addDontObfuscate()
            .run(parameters.getRuntime(), Main.class);
    checkRunResult(result);
  }

  @Test
  public void testKeepSerializedNameProguard()
      throws IOException, CompilationFailedException, ExecutionException {
    assumeTrue(parameters.isCfRuntime());
    ProguardTestRunResult result =
        testForProguard()
            .addInnerClasses(SerializedNameAlternateTest.class)
            .addKeepAttributes("*Annotation*")
            .addKeepMainRule(Main.class)
            .addKeepClassRules(Foo.class)
            .addKeepClassRules(SerializedName.class)
            .addDontObfuscate()
            .addKeepRules("-dontwarn")
            .run(parameters.getRuntime(), Main.class);
    checkRunResult(result);
  }

  private void checkRunResult(TestRunResult<?> testRunResult)
      throws IOException, ExecutionException {
    testRunResult
        .assertSuccessWithOutputLines(
            EXPECTED_DEFAULT_VALUE_ANNOTATION, EXPECTED_SERIALIZED_ANNOTATION)
        .inspect(
            codeInspector -> {
              FieldSubject bar = codeInspector.clazz(Foo.class).uniqueFieldWithOriginalName("bar");
              AnnotationSubject annotation = bar.annotation(SerializedName.class.getTypeName());
              assertThat(annotation, isPresent());
              assertEquals(0, annotation.getAnnotation().elements.length);
            });
  }
}
