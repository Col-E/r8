// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Regression test from b/296358866
@RunWith(Parameterized.class)
public class RetraceNestAccessorTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private final String mapping =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.example.Foo -> com.example.Foo:",
          "# {'id':'sourceFile','fileName':'Foo.java'}",
          "    372:387:boolean com.example.Foo$4.isNeeded(int):584:584 -> -$$Nest$mfoo",
          "    372:387:void foo(boolean):575 -> -$$Nest$mfoo",
          "    372:387:void -$$Nest$mfoo(com.example.Foo,boolean):0 -> -$$Nest$mfoo",
          "    # {'id':'com.android.tools.r8.synthesized'}",
          "com.example.Foo$1 -> com.example.Foo$1:",
          "# {'id':'sourceFile','fileName':'Foo.java'}",
          "    1247:1249:void com.example.Foo$4.start(com.example.Baz):185:185 -> handle",
          "    1247:1249:void com.example.Bar.start(com.example.Baz):263 -> handle",
          "    1247:1249:void com.example.Bar.replace(com.example.Baz):238 -> handle",
          "    1247:1249:void com.example.Bar.update(com.example.Baz,boolean):223 -> handle",
          "    1247:1249:void com.example.Bar.put(com.example.Baz,boolean,boolean):127 -> handle",
          "    1247:1249:void com.example.Foo.start(com.example.Baz):479 -> handle",
          "    1247:1249:void handle(android.os.Message):114 -> handle");

  private final String stacktrace =
      StringUtils.lines(
          "at android.provider.Settings$Secure.getIntForUser(Settings.java:9660)",
          // R8 prefixes methods with - in nest access bridges.
          "at com.example.Foo.-$$Nest$mfoo(SourceFile:379)",
          "at com.example.Foo$1.handle(SourceFile:1247)",
          "at android.os.Handler.dispatchMessage(Handler.java:106)");

  private final String retraced =
      StringUtils.lines(
          "at android.provider.Settings$Secure.getIntForUser(Settings.java:9660)",
          "at com.example.Foo$4.isNeeded(Foo.java:584)",
          "at com.example.Foo.foo(Foo.java:575)",
          "at com.example.Foo$4.start(Foo.java:185)",
          "at com.example.Bar.start(Bar.java:263)",
          "at com.example.Bar.replace(Bar.java:238)",
          "at com.example.Bar.update(Bar.java:223)",
          "at com.example.Bar.put(Bar.java:127)",
          "at com.example.Foo.start(Foo.java:479)",
          "at com.example.Foo$1.handle(Foo.java:114)",
          "at android.os.Handler.dispatchMessage(Handler.java:106)");

  @Test
  public void testAmbiguousResult() {
    Retrace.run(
        RetraceCommand.builder()
            .setMappingSupplier(
                ProguardMappingSupplier.builder()
                    .setProguardMapProducer(ProguardMapProducer.fromString(mapping))
                    .build())
            .setStackTrace(StringUtils.splitLines(stacktrace))
            .setRetracedStackTraceConsumer(
                result -> {
                  assertEquals(retraced, StringUtils.lines(result));
                })
            .build());
  }
}
