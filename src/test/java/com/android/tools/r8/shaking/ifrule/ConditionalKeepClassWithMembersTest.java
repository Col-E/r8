// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConditionalKeepClassWithMembersTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8KeepingInit() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(SerializedName.class)
        .addKeepRules(
            StringUtils.lines(
                "-if class * {",
                "  @ " + typeName(SerializedName.class) + " <fields>;",
                "}",
                "-keepclasseswithmembers class <1> {",
                "  <init>();",
                "}"))
        .addKeepRules(
            StringUtils.lines(
                "-if class *",
                "-keepclasseswithmembers,allowshrinking class <1> {",
                "  @" + typeName(SerializedName.class) + " <fields>;",
                "}"))
        .compile()
        .inspect(
            inspector -> {
              ClassSubject dataClass = inspector.clazz(Data.class);
              assertThat(dataClass, isPresent());
              assertThat(dataClass.uniqueFieldWithOriginalName("field"), isPresent());
              assertThat(dataClass.uniqueFieldWithOriginalName("unusedField"), isAbsent());
              assertThat(inspector.clazz(Data2.class), isAbsent());
            })
        .run(parameters.getRuntime(), Main.class, typeName(Data.class))
        .assertSuccessWithOutputLines("0");
  }

  @Test
  public void testR8NotKeepingInit() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(SerializedName.class)
        .addKeepRules(
            StringUtils.lines(
                "-if class *",
                "-keepclasseswithmembers,allowshrinking class <1> {",
                "  <init>();",
                "  @" + typeName(SerializedName.class) + " <fields>;",
                "}"))
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(Data.class), isAbsent());
              assertThat(inspector.clazz(Data2.class), isAbsent());
            })
        .run(parameters.getRuntime(), Main.class, typeName(Data.class))
        .assertFailureWithErrorThatThrows(ClassNotFoundException.class);
  }

  public @interface SerializedName {}

  public static class Data {

    @SerializedName public int field;
  }

  public static class Data2 {

    @SerializedName public int field;
    @SerializedName public int unusedField;
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      check((Data) (Class.forName(args[0])).getDeclaredConstructor().newInstance());
    }

    public static void check(Data f) {
      System.out.println(f.field);
    }
  }
}
