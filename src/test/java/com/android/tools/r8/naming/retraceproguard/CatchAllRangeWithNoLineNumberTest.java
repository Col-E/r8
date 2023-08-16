// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retraceproguard;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CatchAllRangeWithNoLineNumberTest extends TestBase {

  private final ProguardVersion proguardVersion;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), ProguardVersion.values());
  }

  public CatchAllRangeWithNoLineNumberTest(
      TestParameters parameters, ProguardVersion proguardVersion) {
    this.proguardVersion = proguardVersion;
    parameters.assertNoneRuntime();
  }

  private final String[] stackTrace =
      new String[] {
        "\tat a.a(SourceFile)",
        "\tat b.a(SourceFile)",
        "\tat c.a(SourceFile)",
        "\tat a.a(SourceFile:1)",
        "\tat a.a(SourceFile:0)"
      };

  private final String mapping =
      StringUtils.joinLines(
          "foo.bar.Baz -> a:",
          "  0:65535:void foo():33:33 -> a",
          "foo.bar.Qux -> b:",
          "  1:65535:void foo():33:33 -> a",
          "foo.bar.Quux -> c:",
          "  void foo():33:33 -> a");

  private final String retraced5_2_1 =
      StringUtils.lines(
          "foo.bar.Baz.a(Baz.java)",
          "foo.bar.Qux.a(Qux.java)",
          "foo.bar.Quux.a(Quux.java)",
          "foo.bar.Baz.foo(Baz.java:33)",
          "foo.bar.Baz.a(Baz.java:0)");

  private final String retraced6_0_1 =
      StringUtils.lines(
          "foo.bar.Baz.a(Baz.java)",
          "foo.bar.Qux.a(Qux.java)",
          "foo.bar.Quux.a(Quux.java)",
          "foo.bar.Baz.foo(Baz.java:33)",
          "foo.bar.Baz.a(Baz.java:0)");

  private final String retraced7_0_0 =
      StringUtils.lines(
          "foo.bar.Baz.foo(Baz.java)",
          "foo.bar.Qux.foo(Qux.java)",
          "foo.bar.Quux.a(Quux.java)",
          "foo.bar.Baz.foo(Baz.java:33)",
          "foo.bar.Baz.foo(Baz.java:33)");

  private final String retracedR8 =
      StringUtils.lines(
          "\tat foo.bar.Baz.foo(Baz.java:33)",
          "\tat foo.bar.Qux.foo(Qux.java)",
          "\tat foo.bar.Quux.foo(Quux.java:33)",
          "\tat foo.bar.Baz.foo(Baz.java:33)",
          "\tat foo.bar.Baz.foo(Baz.java:33)");

  @Test
  public void testCatchAllRange() throws IOException {
    StackTrace actualStackTrace = StackTrace.extractFromJvm(StringUtils.lines(stackTrace));
    StackTrace retraced =
        actualStackTrace.retrace(proguardVersion, mapping, temp.newFolder().toPath());
    assertEquals(getExpected(), retraced.toString());
  }

  @Test
  public void testCatchAllRangeR8() {
    Box<String> retracedString = new Box<>();
    Retrace.run(
        RetraceCommand.builder()
            .setRetracedStackTraceConsumer(
                retraced -> retracedString.set(StringUtils.lines(retraced)))
            .setMappingSupplier(
                ProguardMappingSupplier.builder()
                    .setProguardMapProducer(ProguardMapProducer.fromString(mapping))
                    .build())
            .setStackTrace(Arrays.asList(stackTrace))
            .build());
    assertEquals(retracedR8, retracedString.get());
  }

  private String getExpected() {
    switch (proguardVersion) {
      case V5_2_1:
        return retraced5_2_1;
      case V6_0_1:
        return retraced6_0_1;
      case V7_0_0:
        return retraced7_0_0;
      default:
        assertEquals(ProguardVersion.V7_3_2, proguardVersion);
        return retraced7_0_0;
    }
  }
}
