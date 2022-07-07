// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.experimental.startup.StartupItem;
import com.android.tools.r8.experimental.startup.StartupMethod;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    List<StartupItem<ClassReference, MethodReference, ?>> startupList = new ArrayList<>();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .applyIf(
            logcat,
            StartupTestingUtils.enableStartupInstrumentationUsingLogcat(parameters),
            StartupTestingUtils.enableStartupInstrumentationUsingFile(parameters))
        .release()
        .setMinApi(parameters.getApiLevel())
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

  private List<StartupMethod<ClassReference, MethodReference>> getExpectedStartupList()
      throws NoSuchMethodException {
    return ImmutableList.of(
        StartupMethod.referenceBuilder()
            .setMethodReference(MethodReferenceUtils.classConstructor(Main.class))
            .build(),
        StartupMethod.referenceBuilder()
            .setMethodReference(MethodReferenceUtils.mainMethod(Main.class))
            .build(),
        StartupMethod.referenceBuilder()
            .setMethodReference(MethodReferenceUtils.classConstructor(AStartupClass.class))
            .build(),
        StartupMethod.referenceBuilder()
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
