// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.r8ondex;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.cf.bootstrap.BootstrapCurrentEqualityTest;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// TODO(b/142621961): Parametrize at least L and P instead of just P.
@RunWith(Parameterized.class)
public class R8CompiledThroughDexTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // We do not use withDexRuntimesStartingAtIncluding to exclude dex-default and therefore
    // avoid this 2 * 8 minutes test running on tools/test.py.
    return getTestParameters()
        .withDexRuntime(Version.V8_1_0)
        .withDexRuntime(Version.V9_0_0)
        .withAllApiLevels()
        .build();
  }

  public R8CompiledThroughDexTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static String commandLinePathFor(String string) {
    // We switch to absolute path due to the art frameworks requiring to run the command in a
    // different folder.
    return Paths.get(string).toAbsolutePath().toString();
  }

  private static final String R8_KEEP = Paths.get("src/main/keep.txt").toAbsolutePath().toString();

  @Test
  public void testR8CompiledWithR8Dex() throws Exception {
    // Compile once R8_WITH_RELOCATED_DEPS_JAR using normal R8_WITH_RELOCATED_DEPS_JAR to dex,
    // and once R8_WITH_RELOCATED_DEPS_JAR with the previously compiled version to dex.
    // Both applications should be identical.

    // We use runJava and runArtRaw to show explicitly we run the exact same commands.
    // We use extra VM parameters for memory. The command parameters should look like:
    // -Xmx512m com...R8 --release --min-api 1 --output path/to/folder --lib rt.jar
    // --pg-conf R8KeepRules r8.jar
    // The 512m memory is required to make it work on ART since default is lower.

    File ouputFolder = temp.newFolder("output");

    // Compile R8 to dex on the JVM.
    Path ouputThroughCf = ouputFolder.toPath().resolve("outThroughCf.zip").toAbsolutePath();
    ProcessResult javaProcessResult =
        ToolHelper.runJava(
            TestRuntime.getCheckedInJdk9(),
            Collections.singletonList(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR),
            "-Xmx512m",
            R8.class.getTypeName(),
            "--release",
            "--min-api",
            Integer.toString(parameters.getApiLevel().getLevel()),
            "--output",
            ouputThroughCf.toString(),
            "--lib",
            ToolHelper.JAVA_8_RUNTIME,
            "--pg-conf",
            R8_KEEP,
            ToolHelper.R8_WITH_RELOCATED_DEPS_JAR.toAbsolutePath().toString());
    if (javaProcessResult.exitCode != 0) {
      System.out.println(javaProcessResult);
    }
    assertEquals(0, javaProcessResult.exitCode);

    // Compile R8 to Dex on Dex, using the previous dex artifact.
    // We need the extra parameter --64 to use 64 bits frameworks.
    Path ouputThroughDex = ouputFolder.toPath().resolve("outThroughDex.zip").toAbsolutePath();
    ProcessResult artProcessResult =
        ToolHelper.runArtRaw(
            Collections.singletonList(ouputThroughCf.toAbsolutePath().toString()),
            R8.class.getTypeName(),
            (ToolHelper.ArtCommandBuilder builder) ->
                builder.appendArtOption("--64").appendArtOption("-Xmx512m"),
            parameters.getRuntime().asDex().getVm(),
            true,
            "--release",
            "--min-api",
            Integer.toString(parameters.getApiLevel().getLevel()),
            "--output",
            ouputThroughDex.toString(),
            "--lib",
            commandLinePathFor(ToolHelper.JAVA_8_RUNTIME),
            "--pg-conf",
            commandLinePathFor(R8_KEEP),
            ToolHelper.R8_WITH_RELOCATED_DEPS_JAR.toAbsolutePath().toString());
    if (artProcessResult.exitCode != 0) {
      System.out.println(artProcessResult);
    }
    assertEquals(0, artProcessResult.exitCode);

    // Ensure both generated artifacts are equal.
    assertTrue(BootstrapCurrentEqualityTest.filesAreEqual(ouputThroughCf, ouputThroughDex));
  }
}
