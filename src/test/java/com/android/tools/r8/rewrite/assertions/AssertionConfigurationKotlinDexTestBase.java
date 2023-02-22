// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import java.util.Collection;
import org.junit.Test;
import org.junit.runners.Parameterized;

public abstract class AssertionConfigurationKotlinDexTestBase
    extends AssertionConfigurationKotlinTestBase {

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public AssertionConfigurationKotlinDexTestBase(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean kotlinStdlibAsClasspath,
      boolean useJvmAssertions) {
    super(parameters, kotlinParameters, kotlinStdlibAsClasspath, useJvmAssertions);
  }

  @Test
  public void testD8PassthroughAllAssertions() throws Exception {
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::passthroughAllAssertions),
        inspector -> checkAssertionCodeLeft(inspector, false),
        // Leaving assertions in on Dalvik/Art means no assertions.
        noAllAssertionsExpectedLines());
  }

  @Test
  public void testR8PassthroughAllAssertions() throws Exception {
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::passthroughAllAssertions),
        inspector -> checkAssertionCodeLeft(inspector, true),
        // Leaving assertions in on Dalvik/Art means no assertions.
        noAllAssertionsExpectedLines());
  }

  @Test
  public void testD8CompileTimeDisableAllAssertions() throws Exception {
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeDisableAllAssertions),
        inspector -> checkAssertionCodeRemoved(inspector, false),
        // Compile time disabling assertions on Dalvik/Art means no assertions.
        noAllAssertionsExpectedLines());
  }

  @Test
  public void testR8CompileTimeDisableAllAssertions() throws Exception {
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeDisableAllAssertions),
        inspector -> checkAssertionCodeRemoved(inspector, true),
        // Compile time disabling assertions on Dalvik/Art means no assertions.
        noAllAssertionsExpectedLines());
  }

  @Test
  public void testD8CompileTimeEnableAllAssertions() throws Exception {
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions),
        inspector -> checkAssertionCodeEnabled(inspector, false),
        // Compile time enabling assertions gives assertions on Dalvik/Art.
        allAssertionsExpectedLines());
  }

  @Test
  public void testR8CompileTimeEnableAllAssertions() throws Exception {
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions),
        inspector -> checkAssertionCodeEnabled(inspector, true),
        // Compile time enabling assertions gives assertions on Dalvik/Art.
        allAssertionsExpectedLines());
  }

  @Test
  public void testD8CompileTimeEnableForAllClasses() throws Exception {
    if (useJvmAssertions) {
      // Enabling for the kotlin generated Java classes should enable all.
      runD8Test(
          builder ->
              builder
                  .addAssertionsConfiguration(
                      b -> b.setCompileTimeEnable().setScopeClass(class1).build())
                  .addAssertionsConfiguration(
                      b -> b.setCompileTimeEnable().setScopeClass(class2).build()),
          inspector -> {
            // The default is applied to kotlin._Assertions (which for DEX is remove).
            if (!kotlinStdlibAsLibrary) {
              checkAssertionCodeRemoved(inspector, "kotlin._Assertions", false);
            }
            checkAssertionCodeEnabled(inspector, class1, false);
            checkAssertionCodeEnabled(inspector, class2, false);
          },
          allAssertionsExpectedLines());
    } else {
      // Enabling for the class kotlin._Assertions should enable all.
      runD8Test(
          builder ->
              builder.addAssertionsConfiguration(
                  b -> b.setCompileTimeEnable().setScopeClass("kotlin._Assertions").build()),
          inspector -> checkAssertionCodeEnabled(inspector, false),
          allAssertionsExpectedLines());
    }
  }

  @Test
  public void testR8CompileTimeEnableForAllClasses() throws Exception {
    if (useJvmAssertions) {
      runR8Test(
          builder ->
              builder
                  .addAssertionsConfiguration(
                      b -> b.setCompileTimeEnable().setScopeClass(class1).build())
                  .addAssertionsConfiguration(
                      b -> b.setCompileTimeEnable().setScopeClass(class2).build()),
          inspector -> checkAssertionCodeEnabled(inspector, true),
          // When kotlinc generate JVM assertions compile time enabling assertions for the kotlinc
          // generated Java classes should enable all.
          allAssertionsExpectedLines());
    } else {
      runR8Test(
          builder ->
              builder.addAssertionsConfiguration(
                  b -> b.setCompileTimeEnable().setScopeClass("kotlin._Assertions").build()),
          inspector -> checkAssertionCodeEnabled(inspector, true),
          // When kotlinc generate Kotlin assertions compile time enabling assertions the class
          // kotlin._Assertions should enable all.
          allAssertionsExpectedLines());
    }
  }

  @Test
  public void testD8CompileTimeEnableForPackage() throws Exception {
    if (useJvmAssertions) {
      runD8Test(
          builder ->
              builder.addAssertionsConfiguration(
                  b -> b.setCompileTimeEnable().setScopePackage(kotlintestclasesPackage).build()),
          inspector -> {
            // The default is applied to kotlin._Assertions (which for DEX is remove).
            if (!kotlinStdlibAsLibrary) {
              checkAssertionCodeRemoved(inspector, "kotlin._Assertions", false);
            }
            checkAssertionCodeEnabled(inspector, class1, false);
            checkAssertionCodeEnabled(inspector, class2, false);
          },
          // When kotlinc generate JVM assertions compile time enabling assertions for the kotlinc
          // generated test classes package should enable all.
          allAssertionsExpectedLines());
    } else {
      runD8Test(
          builder ->
              builder.addAssertionsConfiguration(
                  b -> b.setCompileTimeEnable().setScopePackage("kotlin").build()),
          inspector -> checkAssertionCodeEnabled(inspector, false),
          // When kotlinc generate Kotlin assertions compile time enabling assertions the package
          // kotlin should enable all.
          allAssertionsExpectedLines());
    }
  }

  @Test
  public void testR8CompileTimeEnableForPackage() throws Exception {
    if (useJvmAssertions) {
      runR8Test(
          builder ->
              builder.addAssertionsConfiguration(
                  b -> b.setCompileTimeEnable().setScopePackage(kotlintestclasesPackage).build()),
          inspector -> checkAssertionCodeEnabled(inspector, true),
          // When kotlinc generate JVM assertions compile time enabling assertions for the kotlinc
          // generated test classes package should enable all.
          allAssertionsExpectedLines());
    } else {
      // When kotlinc generate Kotlin assertions compile time enabling assertions the package
      // kotlin should enable all.
      runR8Test(
          builder ->
              builder.addAssertionsConfiguration(
                  b -> b.setCompileTimeEnable().setScopePackage("kotlin").build()),
          inspector -> checkAssertionCodeEnabled(inspector, true),
          allAssertionsExpectedLines());
    }
  }
}
