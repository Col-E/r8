// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.CLASSES_PATH;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.CLASS_NAMES;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm;
import java.nio.file.Path;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestCompilationErrorTest extends TestBase {

  public NestCompilationErrorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntime(DexVm.Version.first())
        .withDexRuntime(DexVm.Version.last())
        .withAllApiLevels()
        .build();
  }

  @Test
  public void testErrorD8() {
    // TODO (b/132147492): use diagnosis handler
    Assume.assumeTrue(parameters.isDexRuntime());
    testMissingNestHostError(true);
    testIncompleteNestError(true);
  }

  @Test
  public void testErrorR8() {
    // TODO (b/132147492): use diagnosis handler
    Assume.assumeTrue(parameters.isDexRuntime());
    testMissingNestHostError(false);
    testIncompleteNestError(false);
  }

  private void compileOnlyClassesMatching(Matcher<String> matcher, boolean d8) throws Exception {
    List<Path> matchingClasses =
        CLASS_NAMES.stream()
            .filter(matcher::matches)
            .map(name -> CLASSES_PATH.resolve(name + CLASS_EXTENSION))
            .collect(toList());
    if (d8) {
      testForD8()
          .setMinApi(parameters.getApiLevel())
          .addProgramFiles(matchingClasses)
          .addOptionsModification(options -> options.enableNestBasedAccessDesugaring = true)
          .compile();
    } else {
      testForR8(parameters.getBackend())
          .noTreeShaking()
          .noMinification()
          .addKeepAllAttributes()
          .setMinApi(parameters.getApiLevel())
          .addProgramFiles(matchingClasses)
          .addOptionsModification(options -> options.enableNestBasedAccessDesugaring = true)
          .compile();
    }
  }

  private void testMissingNestHostError(boolean d8) {
    try {
      Matcher<String> innerClassMatcher =
          containsString("BasicNestHostWithInnerClassMethods$BasicNestedClass");
      compileOnlyClassesMatching(innerClassMatcher, d8);
      fail("Should have raised an exception for missing nest host");
    } catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("requires its nest host"));
    }
  }

  private void testIncompleteNestError(boolean d8) {
    try {
      Matcher<String> innerClassMatcher = endsWith("BasicNestHostWithInnerClassMethods");
      compileOnlyClassesMatching(innerClassMatcher, d8);
      fail("Should have raised an exception for incomplete nest");
    } catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("requires its nest mates"));
    }
  }
}
