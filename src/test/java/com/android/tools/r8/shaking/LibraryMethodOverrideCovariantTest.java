// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LibraryMethodOverrideCovariantTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LibraryMethodOverrideCovariantTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean supportsKeySetView() {
    return parameters.isCfRuntime()
        || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V10_0_0);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addRunClasspathFiles(buildOnDexRuntime(parameters, LibraryUser.class))
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            supportsKeySetView(),
            result -> result.assertFailureWithErrorThatMatches(containsString("Hello World")),
            result -> result.assertFailureWithErrorThatThrows(NoSuchMethodError.class));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getMainWithoutSyntheticBridgeForKeySet())
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(LibraryUser.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .compile()
        .addRunClasspathFiles(buildOnDexRuntime(parameters, LibraryUser.class))
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            supportsKeySetView(),
            result -> result.assertFailureWithErrorThatMatches(containsString("Hello World")),
            result -> result.assertFailureWithErrorThatThrows(NoSuchMethodError.class));
  }

  private byte[] getMainWithoutSyntheticBridgeForKeySet() throws Exception {
    return transformer(Main.class)
        .removeMethods(
            (access, name, descriptor, signature, exceptions) ->
                descriptor.equals("()Ljava/util/Set;"))
        .transform();
  }

  public static class Main extends ConcurrentHashMap<String, String> {

    @Override
    @NeverInline
    public KeySetView<String, String> keySet() {
      throw new RuntimeException("Hello World");
    }

    public static void main(String[] args) {
      LibraryUser.checkKeySet(new Main());
    }
  }

  public static class LibraryUser {

    public static void checkKeySet(ConcurrentHashMap<?, ?> map) {
      KeySetView<?, ?> objects = map.keySet();
    }
  }
}
