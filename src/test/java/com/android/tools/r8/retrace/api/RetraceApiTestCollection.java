// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.compilerapi.BinaryCompatibilityTestCollection;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.rules.TemporaryFolder;

public class RetraceApiTestCollection
    extends BinaryCompatibilityTestCollection<RetraceApiBinaryTest> {

  private static final Path BINARY_COMPATIBILITY_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "retrace", "binary_compatibility", "tests.jar");

  public static List<Class<? extends RetraceApiBinaryTest>> CLASSES_FOR_BINARY_COMPATIBILITY =
      ImmutableList.of(
          RetraceApiEmptyTest.RetraceTest.class,
          RetraceApiSourceFileTest.ApiTest.class,
          RetraceApiSourceFileNotFoundTest.ApiTest.class,
          RetraceApiInferSourceFileTest.ApiTest.class,
          RetraceApiSynthesizedClassTest.ApiTest.class,
          RetraceApiSynthesizedFieldTest.ApiTest.class,
          RetraceApiSynthesizedMethodTest.ApiTest.class,
          RetraceApiSynthesizedFrameTest.ApiTest.class,
          RetraceApiUnknownJsonTest.ApiTest.class,
          RetraceApiRewriteFrameInlineNpeTest.ApiTest.class,
          RetraceApiAmbiguousOriginalRangeTest.ApiTest.class,
          RetraceApiRewriteFrameInlineNpeResidualTest.ApiTest.class,
          RetraceApiOutlineNoInlineTest.ApiTest.class,
          RetraceApiOutlineInlineTest.ApiTest.class,
          RetraceApiOutlineInOutlineStackTrace.ApiTest.class,
          RetraceApiInlineInOutlineTest.ApiTest.class,
          RetraceApiSingleFrameTest.ApiTest.class,
          RetracePartitionStringTest.ApiTest.class,
          RetracePartitionRoundTripTest.ApiTest.class,
          RetracePartitionJoinNoMetadataTest.ApiTest.class,
          RetracePartitionSerializedObfuscatedKeyTest.ApiTest.class,
          RetracePartitionRoundTripInlineTest.ApiTest.class,
          RetraceApiTypeResultTest.ApiTest.class,
          RetraceApiResidualSignatureTest.ApiTest.class,
          RetracePartitionEmptyMappingTest.ApiTest.class,
          RetraceApiProxyFrameWithSourceFileTest.ApiTest.class);

  public static List<Class<? extends RetraceApiBinaryTest>> CLASSES_PENDING_BINARY_COMPATIBILITY =
      ImmutableList.of();

  private final TemporaryFolder temp;

  public RetraceApiTestCollection(TemporaryFolder temp) {
    this.temp = temp;
  }

  @Override
  public TemporaryFolder getTemp() {
    return temp;
  }

  @Override
  public Path getTargetJar() {
    return ToolHelper.getRetracePath();
  }

  @Override
  public Path getCheckedInTestJar() {
    return BINARY_COMPATIBILITY_JAR;
  }

  @Override
  public List<Class<? extends RetraceApiBinaryTest>> getCheckedInTestClasses() {
    return CLASSES_FOR_BINARY_COMPATIBILITY;
  }

  @Override
  public List<Class<? extends RetraceApiBinaryTest>> getPendingTestClasses() {
    return CLASSES_PENDING_BINARY_COMPATIBILITY;
  }

  @Override
  public List<Class<?>> getAdditionalClassesForTests() {
    return ImmutableList.of(RetraceApiBinaryTest.class);
  }

  @Override
  public List<Class<?>> getPendingAdditionalClassesForTests() {
    return ImmutableList.of();
  }

  @Override
  public List<String> getVmArgs() {
    return ImmutableList.of();
  }
}
