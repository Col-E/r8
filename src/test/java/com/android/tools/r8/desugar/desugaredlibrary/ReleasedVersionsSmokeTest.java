// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.LibraryDesugaringTestConfiguration.Configuration;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReleasedVersionsSmokeTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final Configuration configuration;
  private static final String expectedOutput =
      StringUtils.lines(
          "true",
          "Caught java.time.format.DateTimeParseException",
          "true",
          "1970-01-02T10:17:36.789Z",
          "GMT",
          "GMT",
          "1000",
          "Hello, world");

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        Configuration.getReleased(),
        getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.B).build());
  }

  public ReleasedVersionsSmokeTest(Configuration configuration, TestParameters parameters) {
    this.configuration = configuration;
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(ReleasedVersionsSmokeTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.builder()
                .setMinApi(parameters.getApiLevel())
                .setConfiguration(configuration)
                .withKeepRuleConsumer()
                .setMode(CompilationMode.DEBUG)
                .build())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            configuration.equals(Configuration.RELEASED_1_1_5),
            r -> r.assertSuccessWithOutput(expectedOutput),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(ReleasedVersionsSmokeTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.builder()
                .setMinApi(parameters.getApiLevel())
                .withKeepRuleConsumer()
                .setMode(CompilationMode.RELEASE)
                .build())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(Clock.systemDefaultZone().getZone().equals(ZoneId.systemDefault()));
      try {
        java.time.LocalDate.parse("");
      } catch (java.time.format.DateTimeParseException e) {
        System.out.println("Caught java.time.format.DateTimeParseException");
      }
      System.out.println(java.time.ZoneOffset.getAvailableZoneIds().size() > 0);
      System.out.println(
          java.util.Date.from(new java.util.Date(123456789).toInstant()).toInstant());

      java.util.TimeZone timeZone = java.util.TimeZone.getTimeZone(ZoneId.of("GMT"));
      System.out.println(timeZone.getID());
      System.out.println(timeZone.toZoneId().getId());

      System.out.println(Duration.ofMillis(1000).toMillis());

      System.out.println("Hello, world");
    }
  }
}
