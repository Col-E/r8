// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AnnotationClassKeepRuleTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public ProguardVersion proguardVersion;

  @Parameter(2)
  public boolean testAnnotation;

  @Parameters(name = "{0}, PG: {1}, use @interface: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        ProguardVersion.values(),
        BooleanUtils.values());
  }

  @Test
  public void testR8Interface() throws Exception {
    assumeTrue(proguardVersion == ProguardVersion.getLatest());
    runTest(testForR8(parameters.getBackend())).inspect(this::inspect);
  }

  @Test
  public void testPGInterface() throws Exception {
    runTest(testForProguard(proguardVersion).addDontWarn(AnnotationClassKeepRuleTest.class))
        .inspect(this::inspect);
  }

  @Test
  public void testR8Annotation() throws Exception {
    assumeTrue(proguardVersion == ProguardVersion.getLatest());
    runTest(testForR8(parameters.getBackend())).inspect(this::inspect);
  }

  @Test
  public void testPGAnnotation() throws Exception {
    runTest(testForProguard(proguardVersion).addDontWarn(AnnotationClassKeepRuleTest.class))
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(Foo.class), not(isPresent()));
    assertThat(inspector.clazz(Bar.class), isPresent());
    assertThat(inspector.clazz(Baz.class), notIf(isPresent(), testAnnotation));
  }

  private TestRunResult<?> runTest(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder)
      throws Exception {
    return testBuilder
        .addInnerClasses(getClass())
        .setMinApi(AndroidApiLevel.B)
        .addKeepRules(
            "-keep @ "
                + typeName(Foo.class)
                + " "
                + (testAnnotation ? "@interface" : "interface")
                + " *")
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println("Hello World!");
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface Foo {}

  @Foo
  public @interface Bar {}

  @Foo
  public interface Baz {}
}
