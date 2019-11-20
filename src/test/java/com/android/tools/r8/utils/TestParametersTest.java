// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

// This test is should explicitly not be parameterized and using the test parameters as it is
// testing the correctness of the test-parameters set up.
public class TestParametersTest {

  @Test
  public void testNoneRuntime() {
    assumeFalse(
        "Test is only valid when no runtimes property is set",
        TestParametersBuilder.isRuntimesPropertySet());
    TestParametersCollection params = TestParametersBuilder.builder().withNoneRuntime().build();
    assertTrue(params.stream().anyMatch(TestParameters::isNoneRuntime));
  }

  @Test
  public void testAllRuntimes() {
    assumeFalse(
        "Test is only valid when no runtimes property is set",
        TestParametersBuilder.isRuntimesPropertySet());
    TestParametersCollection params = TestParametersBuilder.builder().withAllRuntimes().build();
    assertTrue(params.stream().noneMatch(TestParameters::isNoneRuntime));
    assertTrue(params.stream().anyMatch(TestParameters::isDexRuntime));
    assertTrue(params.stream().anyMatch(TestParameters::isCfRuntime));
  }

  @Test
  public void testAllApiLevels() {
    assumeFalse(
        "Test is only valid when no runtimes property is set",
        TestParametersBuilder.isRuntimesPropertySet());
    // This test may also fail once the tests can be configured for with API levels to run.
    TestParametersCollection params =
        TestParametersBuilder.builder().withAllRuntimesAndApiLevels().build();
    assertTrue(params.stream().noneMatch(TestParameters::isNoneRuntime));
    assertTrue(params.stream().anyMatch(p -> p.isCfRuntime() && p.getApiLevel() == null));
    // Default API levels are min and max for each DEX VM.
    Map<DexRuntime, Set<AndroidApiLevel>> levels = new IdentityHashMap<>();
    params.stream()
        .forEach(
            p -> {
              if (p.isDexRuntime()) {
                levels
                    .computeIfAbsent(p.getRuntime().asDex(), key -> new HashSet<>())
                    .add(p.getApiLevel());
              }
            });
    assertFalse(levels.isEmpty());
    levels.forEach(
        (dexRuntime, apiLevels) -> {
          assertThat(apiLevels, hasItem(AndroidApiLevel.getDefault()));
          assertThat(apiLevels, hasItem(dexRuntime.getMinApiLevel()));
        });
  }

  @Test
  public void testJdk9Presence() {
    assumeTrue(!TestParametersBuilder.isRuntimesPropertySet()
        || TestParametersBuilder.getRuntimesProperty().contains("jdk9"));
    assertTrue(TestParametersBuilder
        .builder()
        .withAllRuntimesAndApiLevels()
        .build()
        .stream()
        .anyMatch(parameter -> parameter.getRuntime().equals(TestRuntime.getCheckedInJdk9())));
  }

  @Test
  public void testDexDefaultPresence() {
    assumeTrue(ToolHelper.isLinux());
    assumeTrue(!TestParametersBuilder.isRuntimesPropertySet()
        || TestParametersBuilder.getRuntimesProperty().contains("dex-default"));
    assertTrue(TestParametersBuilder
        .builder()
        .withAllRuntimesAndApiLevels()
        .build()
        .stream()
        .anyMatch(parameter -> parameter.getRuntime().name().equals("dex-default")));
  }

  @Test
  public void testDex444Presence() {
    assumeTrue(ToolHelper.isLinux());
    assumeTrue(!TestParametersBuilder.isRuntimesPropertySet()
        || TestParametersBuilder.getRuntimesProperty().contains("dex-4.4.4"));
    assertTrue(TestParametersBuilder
        .builder()
        .withAllRuntimesAndApiLevels()
        .build()
        .stream()
        .anyMatch(parameter -> parameter.getRuntime().name().equals("dex-4.4.4")));
  }
}
