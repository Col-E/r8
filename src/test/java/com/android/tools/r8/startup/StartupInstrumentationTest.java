// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.profile.ExternalStartupClass;
import com.android.tools.r8.startup.profile.ExternalStartupItem;
import com.android.tools.r8.startup.profile.ExternalStartupMethod;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StartupInstrumentationTest extends TestBase {

  @Parameter(0)
  public boolean logcat;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, logcat: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimesAndAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    Path out = temp.newFolder().toPath().resolve("out.txt").toAbsolutePath();
    Set<ExternalStartupItem> startupList = new LinkedHashSet<>();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .applyIf(
            logcat,
            StartupTestingUtils.enableStartupInstrumentationForOriginalAppUsingLogcat(parameters),
            StartupTestingUtils.enableStartupInstrumentationForOriginalAppUsingFile(parameters))
        .release()
        .setMinApi(parameters)
        .compile()
        .applyIf(
            logcat,
            compileResult ->
                compileResult.addRunClasspathFiles(StartupTestingUtils.getAndroidUtilLog(temp)))
        .run(parameters.getRuntime(), Main.class, Boolean.toString(logcat), out.toString())
        .applyIf(
            logcat,
            StartupTestingUtils.removeStartupListFromStdout(startupList::add),
            runResult -> StartupTestingUtils.readStartupListFromFile(out, startupList::add))
        .assertSuccessWithOutputLines(getExpectedOutput());
    assertEquals(getExpectedStartupList(), startupList);
  }

  private static List<String> getExpectedOutput() {
    return ImmutableList.of("foo");
  }

  private Set<ExternalStartupItem> getExpectedStartupList() throws NoSuchMethodException {
    return ImmutableSet.of(
        ExternalStartupClass.builder()
            .setClassReference(Reference.classFromClass(Main.class))
            .build(),
        ExternalStartupMethod.builder()
            .setMethodReference(MethodReferenceUtils.mainMethod(Main.class))
            .build(),
        ExternalStartupClass.builder()
            .setClassReference(Reference.classFromClass(AStartupClass.class))
            .build(),
        ExternalStartupMethod.builder()
            .setMethodReference(
                Reference.methodFromMethod(AStartupClass.class.getDeclaredMethod("foo")))
            .build());
  }

  static class Main {

    public static void main(String[] args) throws IOException {
      boolean logcat = Boolean.parseBoolean(args[0]);
      AStartupClass.foo();
      if (!logcat) {
        InstrumentationServer.getInstance().writeToFile(new File(args[1]));
      }
    }

    // @Keep
    public void onClick() {
      NonStartupClass.bar();
    }
  }

  static class AStartupClass {

    @NeverInline
    static void foo() {
      System.out.println("foo");
    }
  }

  static class NonStartupClass {

    @NeverInline
    static void bar() {
      System.out.println("bar");
    }
  }
}
