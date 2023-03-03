// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking1Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{2}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShaking1Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking1";
  }

  @Override
  protected String getMainClass() {
    return "shaking1.Shaking";
  }

  @Test
  public void testKeeprules() throws Exception {
    runTest(
        TreeShaking1Test::shaking1HasNoClassUnused,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking1/keep-rules.txt"));
  }

  @Test
  public void testKeeprulesEmpty() throws Exception {
    runTest(
        TreeShaking1Test::shaking1HasNoClassUnused,
        null,
        null,
        ImmutableList.of(
            "src/test/examples/shaking1/keep-rules.txt", "src/test/proguard/valid/empty.flags"));
  }

  @Test
  public void testKeeprulesEmptyEmpty() throws Exception {
    runTest(
        TreeShaking1Test::shaking1HasNoClassUnused,
        null,
        null,
        ImmutableList.of(
            "src/test/examples/shaking1/keep-rules.txt",
            "src/test/proguard/valid/empty.flags",
            "src/test/proguard/valid/empty.flags"));
  }

  @Test
  public void testKeeprulesdontshrink() throws Exception {
    runTest(
        null,
        null,
        TreeShakingTest::checkSameStructure,
        ImmutableList.of("src/test/examples/shaking1/keep-rules-dont-shrink.txt"));
  }

  @Test
  public void testKeeprulesdontshrinkEmpty() throws Exception {
    runTest(
        null,
        null,
        TreeShakingTest::checkSameStructure,
        ImmutableList.of(
            "src/test/examples/shaking1/keep-rules-dont-shrink.txt",
            "src/test/proguard/valid/empty.flags"));
  }

  @Test
  public void testKeeprulesdontshrinkEmptyEmpty() throws Exception {
    runTest(
        null,
        null,
        TreeShakingTest::checkSameStructure,
        ImmutableList.of(
            "src/test/examples/shaking1/keep-rules-dont-shrink.txt",
            "src/test/proguard/valid/empty.flags",
            "src/test/proguard/valid/empty.flags"));
  }

  @Test
  public void testKeeprulesprintusage() throws Exception {
    runTest(
        null, null, null, ImmutableList.of("src/test/examples/shaking1/keep-rules-printusage.txt"));
  }

  @Test
  public void testKeeprulesprintusageEmpty() throws Exception {
    runTest(
        null,
        null,
        null,
        ImmutableList.of(
            "src/test/examples/shaking1/keep-rules-printusage.txt",
            "src/test/proguard/valid/empty.flags"));
  }

  @Test
  public void testKeeprulesprintusageEmptyEmpty() throws Exception {
    runTest(
        null,
        null,
        null,
        ImmutableList.of(
            "src/test/examples/shaking1/keep-rules-printusage.txt",
            "src/test/proguard/valid/empty.flags",
            "src/test/proguard/valid/empty.flags"));
  }

  @Test
  public void testKeeprulesrepackaging() throws Exception {
    Assume.assumeFalse(getMinify() == MinifyMode.NONE);
    runTest(
        TreeShaking1Test::shaking1IsCorrectlyRepackaged,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking1/keep-rules-repackaging.txt"));
  }

  @Test
  public void testKeeprulesrepackagingEmpty() throws Exception {
    Assume.assumeFalse(getMinify() == MinifyMode.NONE);
    runTest(
        TreeShaking1Test::shaking1IsCorrectlyRepackaged,
        null,
        null,
        ImmutableList.of(
            "src/test/examples/shaking1/keep-rules-repackaging.txt",
            "src/test/proguard/valid/empty.flags"));
  }

  @Test
  public void testKeeprulesrepackagingEmptyEmpty() throws Exception {
    Assume.assumeFalse(getMinify() == MinifyMode.NONE);
    runTest(
        TreeShaking1Test::shaking1IsCorrectlyRepackaged,
        null,
        null,
        ImmutableList.of(
            "src/test/examples/shaking1/keep-rules-repackaging.txt",
            "src/test/proguard/valid/empty.flags",
            "src/test/proguard/valid/empty.flags"));
  }

  private static void shaking1IsCorrectlyRepackaged(CodeInspector inspector) {
    inspector.forAllClasses(
        clazz -> {
          String descriptor = clazz.getFinalDescriptor();
          Assert.assertTrue(
              descriptor,
              DescriptorUtils.getUnqualifiedClassNameFromDescriptor(descriptor).equals("Shaking")
                  || DescriptorUtils.getPackageNameFromDescriptor(descriptor).equals("repackaged"));
        });
  }

  private static void shaking1HasNoClassUnused(CodeInspector inspector) {
    Assert.assertFalse(inspector.clazz("shaking1.Unused").isPresent());
    ClassSubject used = inspector.clazz("shaking1.Used");
    Assert.assertTrue(used.isPresent());
    Assert.assertTrue(
        used.method("java.lang.String", "aMethodThatIsNotUsedButKept", Collections.emptyList())
            .isPresent());
    Assert.assertTrue(used.field("int", "aStaticFieldThatIsNotUsedButKept").isPresent());
    // Rewriting of <clinit> moves the initialization of aStaticFieldThatIsNotUsedButKept
    // from <clinit> code into statics value section of the dex file.
    Assert.assertFalse(used.clinit().isPresent());
  }
}
