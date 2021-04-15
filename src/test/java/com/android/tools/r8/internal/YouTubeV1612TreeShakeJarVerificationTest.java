// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static com.android.tools.r8.ToolHelper.isLocalDevelopment;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.analysis.ProtoApplicationStats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class YouTubeV1612TreeShakeJarVerificationTest extends YouTubeCompilationTestBase {

  private static final boolean DUMP = false;
  private static final int MAX_SIZE = 30000000;

  private final Path dumpDirectory = Paths.get("YouTubeV1612-" + System.currentTimeMillis());

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public YouTubeV1612TreeShakeJarVerificationTest(TestParameters parameters) {
    super(16, 12);
    parameters.assertNoneRuntime();
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(isLocalDevelopment());

    KeepRuleConsumer keepRuleConsumer = new PresentKeepRuleConsumer();
    R8TestCompileResult compileResult =
        testForR8(Backend.DEX)
            .addProgramFiles(getProgramFiles())
            .addLibraryFiles(getLibraryFiles())
            .addKeepRuleFiles(getKeepRuleFiles())
            .addIgnoreWarnings()
            .allowDiagnosticMessages()
            .allowUnusedDontWarnPatterns()
            .allowUnusedProguardConfigurationRules()
            .setMinApi(AndroidApiLevel.L)
            .enableCoreLibraryDesugaring(
                AndroidApiLevel.L,
                keepRuleConsumer,
                StringResource.fromFile(getDesugaredLibraryConfiguration()))
            .compile();

    if (ToolHelper.isLocalDevelopment()) {
      if (DUMP) {
        dumpDirectory.toFile().mkdirs();
        compileResult.writeToZip(dumpDirectory.resolve("app.zip"));
        compileResult.writeProguardMap(dumpDirectory.resolve("mapping.txt"));
      }

      DexItemFactory dexItemFactory = new DexItemFactory();
      ProtoApplicationStats original =
          new ProtoApplicationStats(dexItemFactory, new CodeInspector(getProgramFiles()));
      ProtoApplicationStats actual =
          new ProtoApplicationStats(dexItemFactory, compileResult.inspector(), original);
      ProtoApplicationStats baseline =
          new ProtoApplicationStats(
              dexItemFactory, new CodeInspector(getReleaseApk(), getReleaseProguardMap()));
      System.out.println(actual.getStats(baseline));
    }

    int applicationSize = compileResult.getApp().applicationSize();
    System.out.println("Dex size (app, excluding desugared library): " + applicationSize);

    Path desugaredLibrary =
        testForL8(AndroidApiLevel.L)
            .setDesugaredLibraryConfiguration(getDesugaredLibraryConfiguration())
            .setDesugarJDKLibs(getDesugaredLibraryJDKLibs())
            .setDesugarJDKLibsConfiguration(getDesugaredLibraryJDKLibsConfiguration())
            .addKeepRules(keepRuleConsumer.get())
            .addKeepRuleFiles(getDesugaredLibraryKeepRuleFiles())
            .compile();

    byte[] desugaredLibraryDex = ZipUtils.readSingleEntry(desugaredLibrary, "classes.dex");

    if (DUMP) {
      Files.write(dumpDirectory.resolve("desugared_jdk_libs.dex"), desugaredLibraryDex);
    }

    int desugaredLibrarySize = desugaredLibraryDex.length;
    System.out.println("Dex size (desugared library): " + desugaredLibrarySize);

    int totalApplicationSize = applicationSize + desugaredLibrarySize;
    System.out.println("Dex size (total): " + totalApplicationSize);

    assertTrue(
        "Expected max size of " + MAX_SIZE + ", got " + totalApplicationSize,
        totalApplicationSize < MAX_SIZE);
  }
}
