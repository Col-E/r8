// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvalidMappingRangesB309080420Test extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public InvalidMappingRangesB309080420Test(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static String MAPPING =
      StringUtils.unixLines(
          "a.q -> a.q:",
          "    1:1:void a(com.example.Foo) -> a",
          "    2:0:void a() -> a", // Unexpected line range [2:0] - interpreting as [2:2]
          "    12:21:void a(android.content.Intent) -> a",
          "a.x -> a.x:",
          "    1:1:void a(com.example.Foo) -> a",
          "    11:2:void a() -> a", // Unexpected line range [11:2] - interpreting as [2:11]
          "    12:21:void a(android.content.Intent) -> a");

  @Test
  public void test() throws Exception {
    TestDiagnosticMessagesImpl handler = new TestDiagnosticMessagesImpl();
    ProguardMappingSupplier.builder()
        .setProguardMapProducer(ProguardMapProducer.fromString(MAPPING))
        .setLoadAllDefinitions(true)
        .build()
        .createRetracer(handler);
    handler.assertNoMessages();
  }
}
