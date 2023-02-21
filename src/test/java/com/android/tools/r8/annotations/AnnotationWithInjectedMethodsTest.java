// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.annotations;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AnnotationWithInjectedMethodsTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevelsStartingAtIncluding(apiLevelWithDefaultInterfaceMethodsSupport())
        .build();
  }

  public AnnotationWithInjectedMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(AnnotationWithInjectedMethod.class)
        .addKeepRuntimeVisibleAnnotations()
        .addOptionsModification(options -> options.testing.allowInjectedAnnotationMethods = true)
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  private String getExpectedOutput() {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    builder.add(
        "Foo",
        "Bar",
        "Caught IncompleteAnnotationException: "
            + typeName(AnnotationWithInjectedMethod.class)
            + " missing element getInstanceData");
    if (parameters.isCfRuntime()) {
      builder.add("Caught AssertionError: Too many parameters for an annotation method");
    } else {
      builder.add(
          "Caught IllegalArgumentException: Invalid method for annotation type: "
              + "public "
              + (parameters.getRuntime().asDex().getVm().getVersion() == Version.V7_0_0
                  ? ""
                  : "default ")
              + typeName(Data.class)
              + " "
              + typeName(AnnotationWithInjectedMethod.class)
              + ".getInstanceData("
              + typeName(Data.class)
              + ")");
    }
    return StringUtils.lines(builder.build());
  }

  private Collection<Class<?>> getProgramClasses() {
    return ImmutableList.of(Data.class);
  }

  private Collection<byte[]> getProgramClassFileData() throws IOException {
    return ImmutableList.of(
        transformer(Main.class)
            .replaceAnnotationDescriptor(
                descriptor(ToBeAnnotationWithInjectedMethod.class),
                descriptor(AnnotationWithInjectedMethod.class))
            .transform(),
        transformer(AnnotationWithInjectedMethod.class)
            .setAnnotation()
            .removeInnerClasses()
            .replaceAnnotationDescriptor(
                descriptor(ToBeRetention.class), descriptor(Retention.class))
            .transform());
  }

  static class Data {

    String value;

    Data(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface ToBeRetention {
    RetentionPolicy value();
  }

  @ToBeRetention(RetentionPolicy.RUNTIME)
  /*@*/ interface AnnotationWithInjectedMethod extends Annotation {

    default Data getInstanceData() {
      return new Data("Baz");
    }

    default Data getInstanceData(Data in) {
      return new Data(in.value);
    }

    static Data getStaticData() {
      return new Data("Foo");
    }

    static Data getStaticData(Data in) {
      return new Data(in.value);
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface ToBeAnnotationWithInjectedMethod {}

  @ToBeAnnotationWithInjectedMethod
  static class Main {

    public static void main(String[] args) {
      System.out.println(AnnotationWithInjectedMethod.getStaticData());
      System.out.println(AnnotationWithInjectedMethod.getStaticData(new Data("Bar")));

      try {
        System.out.println(getAnnotationWithInjectedMethod().getInstanceData());
      } catch (Throwable e) {
        System.out.println("Caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
      }

      try {
        System.out.println(getAnnotationWithInjectedMethod().getInstanceData(new Data("Qux")));
      } catch (Throwable e) {
        System.out.println("Caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
      }
    }

    static AnnotationWithInjectedMethod getAnnotationWithInjectedMethod() {
      return Main.class.getAnnotation(AnnotationWithInjectedMethod.class);
    }
  }
}
