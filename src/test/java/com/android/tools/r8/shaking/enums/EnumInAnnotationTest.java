// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.enums;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.TestState;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.StringUtils;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Path;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumInAnnotationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean useGenericEnumsRule;

  @Parameters(name = "{0}, use generic enums rule {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  private static final String EXPECTED_RESULT = StringUtils.lines("TEST_ONE", "TEST_TWO");

  public static Path r8cf;
  public static Box<String> minifiedEnumName = new Box<>();

  @BeforeClass
  public static void setUp() throws Exception {
    // Prepare a R8 processed JAR keeping Main with annotations and generic enum rule.
    r8cf =
        R8FullTestBuilder.create(new TestState(getStaticTemp()), Backend.CF)
            .addInnerClasses(EnumInAnnotationTest.class)
            .addKeepMainRule(Main.class)
            .addKeepEnumsRule()
            .addKeepRuntimeVisibleAnnotations()
            .compile()
            .inspect(
                inspector -> {
                  assertThat(inspector.clazz(Enum.class), isPresentAndRenamed());
                  assertThat(
                      inspector.clazz(Enum.class).uniqueFieldWithOriginalName("TEST_ONE"),
                      isPresentAndNotRenamed());
                  assertThat(
                      inspector.clazz(Enum.class).uniqueFieldWithOriginalName("TEST_TWO"),
                      isPresentAndRenamed());
                  minifiedEnumName.set(inspector.clazz(Enum.class).getFinalName());
                })
            .writeToZip();
  }

  @Test
  public void testRuntime() throws Exception {
    assumeTrue(useGenericEnumsRule);
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(!parameters.isDexRuntime() || useGenericEnumsRule);
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumInAnnotationTest.class)
        .addKeepMainRule(Main.class)
        .addKeepEnumsRule()
        .applyIf(
            parameters.isCfRuntime() && useGenericEnumsRule,
            TestShrinkerBuilder::addKeepEnumsRule,
            parameters.isCfRuntime() && !useGenericEnumsRule,
            builder ->
                builder.addKeepRules(
                    "-keepclassmembernames class "
                        + EnumInAnnotationTest.Enum.class.getTypeName()
                        + " { <fields>; }"),
            builder -> {
              // Do nothing for DEX.
              assertTrue(useGenericEnumsRule); // Check not running twice.
            })
        .setMinApi(parameters.getApiLevel())
        .addKeepRuntimeVisibleAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .inspect(
            inspector -> {
              assertThat(
                  inspector.clazz(Enum.class).uniqueFieldWithOriginalName("TEST_ONE"),
                  parameters.isCfRuntime() ? isPresentAndNotRenamed() : isPresentAndRenamed());
              assertThat(
                  inspector.clazz(Enum.class).uniqueFieldWithOriginalName("TEST_TWO"),
                  useGenericEnumsRule ? isPresentAndRenamed() : isPresentAndNotRenamed());
            })
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8WithR8Input() throws Exception {
    assumeTrue(useGenericEnumsRule);
    testForR8(parameters.getBackend())
        .addProgramFiles(r8cf)
        .addKeepMainRule(Main.class)
        .addKeepEnumsRule()
        .setMinApi(parameters.getApiLevel())
        .addKeepRuntimeVisibleAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .inspect(
            inspector -> {
              assertThat(
                  inspector.clazz(minifiedEnumName.get()).uniqueFieldWithOriginalName("TEST_ONE"),
                  parameters.isCfRuntime() ? isPresentAndNotRenamed() : isPresentAndRenamed());
            })
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForProguard(ProguardVersion.V7_0_0)
        .addInnerClasses(EnumInAnnotationTest.class)
        .addKeepMainRule(Main.class)
        .addKeepEnumsRule()
        .applyIf(
            useGenericEnumsRule,
            TestShrinkerBuilder::addKeepEnumsRule,
            builder ->
                builder.addKeepRules(
                    "-keepclassmembernames class " + Enum.class.getTypeName() + " { <fields>; }"))
        .addDontWarn(getClass())
        .addKeepRuntimeVisibleAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public enum Enum {
    TEST_ONE,
    TEST_TWO
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface MyAnnotation {

    Enum value();
  }

  @MyAnnotation(value = Enum.TEST_ONE)
  public static class Main {

    public static void main(String[] args) {
      System.out.println(Main.class.getAnnotation(MyAnnotation.class).value());
      System.out.println(Enum.TEST_TWO);
    }
  }
}
