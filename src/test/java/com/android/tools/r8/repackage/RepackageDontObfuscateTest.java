// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * A simple test that checks that R8 behaves the same as PG for repackaging. We do require that
 * classes that are looked up reflectively are kept in some way, however, for easier debugging with
 * -dontobfuscate we should follow PG behavior. *
 */
@RunWith(Parameterized.class)
public class RepackageDontObfuscateTest extends RepackageTestBase {

  private final String[] EXPECTED = new String[] {"A::foo", "A::foo", "A::foo"};

  @Parameters(name = "{1}, kind: {0}, PG-version: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        ProguardVersion.values());
  }

  private final ProguardVersion proguardVersion;

  public RepackageDontObfuscateTest(
      String flattenPackageHierarchyOrRepackageClasses,
      TestParameters parameters,
      ProguardVersion proguardVersion) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
    this.proguardVersion = proguardVersion;
  }

  @Test
  public void testPG() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForProguard(proguardVersion)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .apply(this::configureRepackaging)
        .addInliningAnnotations()
        .addDontWarn(RepackageDontObfuscateTest.class)
        .addDontObfuscate()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClass = inspector.clazz(A.class);
              assertThat(aClass, isPresentAndNotRenamed());
            })
        .run(parameters.getRuntime(), Main.class, A.class.getTypeName())
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8Full() throws Exception {
    setup(testForR8(parameters.getBackend()))
        .inspect(
            inspector -> {
              ClassSubject aClass = inspector.clazz(A.class);
              assertThat(aClass, isPresentAndRenamed());
            })
        .run(
            parameters.getRuntime(),
            Main.class,
            getRepackagePackage() + "." + A.class.getTypeName())
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8Compat() throws Exception {
    setup(testForR8Compat(parameters.getBackend()))
        .inspect(
            inspector -> {
              ClassSubject aClass = inspector.clazz(A.class);
              assertThat(aClass, isPresentAndNotRenamed());
            })
        .run(parameters.getRuntime(), Main.class, A.class.getTypeName())
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private R8TestCompileResult setup(R8TestBuilder<?> r8TestBuilder) throws Exception {
    return r8TestBuilder
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .apply(this::configureRepackaging)
        .addDontObfuscate()
        .compile();
  }

  public static class A {

    @NeverInline
    public static void foo() {
      System.out.println("A::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      A.foo();
      Class.forName("com.android.tools.r8.repackage.RepackageDontObfuscateTest$A")
          .getDeclaredMethod("foo")
          .invoke(null);
      Class.forName(args[0]).getDeclaredMethod("foo").invoke(null);
    }
  }
}
