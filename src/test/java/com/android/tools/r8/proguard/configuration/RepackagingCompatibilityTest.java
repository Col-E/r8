// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.proguard.configuration;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackagingCompatibilityTest extends TestBase {

  private enum Quote {
    SINGLE,
    DOUBLE,
    NONE
  }

  private static final String expectedOutput = StringUtils.lines("Hello world!");
  private static final Class<?> mainClass = RepackagingCompatabilityTestClass.class;

  private final String directive;
  private final Quote quote;
  private final boolean repackageToRoot;

  @Parameters(name = "Directive: {0}, quote: {1}, repackage to root: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of("-flattenpackagehierarchy", "-repackageclasses"),
        Quote.values(),
        BooleanUtils.values());
  }

  public RepackagingCompatibilityTest(String directive, Quote quote, boolean repackageToRoot) {
    this.directive = directive;
    this.quote = quote;
    this.repackageToRoot = repackageToRoot;
  }

  @Test
  public void testR8() throws Exception {
    runTest(testForR8(Backend.DEX), "R8");
  }

  @Test
  public void testProguard() throws Exception {
    runTest(testForProguard().addKeepRules("-dontwarn " + getClass().getTypeName()), "Proguard");
  }

  private void runTest(TestShrinkerBuilder<?, ?, ?, ?, ?> builder, String shrinker)
      throws Exception {
    assumeFalse(
        String.format(
            "Only repackage to root when there are no quotes"
                + " (repackageToRoot: %s, quote: %s, shrinker: %s)",
            repackageToRoot, quote, shrinker),
        repackageToRoot && quote != Quote.NONE);

    builder
        .addProgramClasses(mainClass)
        .addKeepRules(getKeepRules())
        .run(mainClass)
        .assertSuccessWithOutput(expectedOutput)
        .inspect(
            inspector -> {
              ClassSubject testClassSubject = inspector.clazz(mainClass);
              assertThat(testClassSubject, isPresent());
              if (repackageToRoot) {
                if (directive.equals("-flattenpackagehierarchy")) {
                  assertThat(testClassSubject.getFinalName(), startsWith("a."));
                } else if (directive.equals("-repackageclasses")) {
                  assertThat(testClassSubject.getFinalName(), not(containsString(".")));
                } else {
                  fail();
                }
              } else {
                assertThat(testClassSubject.getFinalName(), startsWith("greeter."));
              }
            });
  }

  private List<String> getKeepRules() {
    return ImmutableList.of(
        // Keep main(), but allow obfuscation
        "-keep,allowobfuscation class " + mainClass.getTypeName() + " {",
        "  public static void main(...);",
        "}",
        // Ensure main() is not renamed
        "-keepclassmembernames class " + mainClass.getTypeName() + " {",
        "  public static void main(...);",
        "}",
        getRepackagingRule());
  }

  private String getRepackagingRule() {
    if (repackageToRoot) {
      return directive;
    }
    switch (quote) {
      case SINGLE:
        return directive + " 'greeter'";
      case DOUBLE:
        return directive + " \"greeter\"";
      case NONE:
        return directive + " greeter";
      default:
        throw new Unreachable();
    }
  }

  public static class RepackagingCompatabilityTestClass {

    public static void main(String[] args) {
      System.out.println("Hello world!");
    }
  }
}
