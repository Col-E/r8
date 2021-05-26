// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static com.android.tools.r8.ToolHelper.isLocalDevelopment;
import static com.android.tools.r8.ToolHelper.shouldRunSlowTests;
import static com.android.tools.r8.internal.proto.ProtoShrinkingTestBase.assertRewrittenProtoSchemasMatch;
import static com.android.tools.r8.internal.proto.ProtoShrinkingTestBase.keepAllProtosRule;
import static com.android.tools.r8.internal.proto.ProtoShrinkingTestBase.keepDynamicMethodSignatureRule;
import static com.android.tools.r8.internal.proto.ProtoShrinkingTestBase.keepNewMessageInfoSignatureRule;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.L8TestCompileResult;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class YouTubeV1612Test extends YouTubeCompilationTestBase {

  private static final int MAX_APPLICATION_SIZE = 29000000;
  private static final int MAX_DESUGARED_LIBRARY_SIZE = 375000;

  private final TestParameters parameters;

  private final Path dumpDirectory = Paths.get("YouTubeV1612-" + System.currentTimeMillis());
  private final Reporter reporter = new Reporter();

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntime(Version.V9_0_0)
        .withApiLevel(AndroidApiLevel.L)
        .build();
  }

  public YouTubeV1612Test(TestParameters parameters) {
    super(16, 12, parameters.getApiLevel());
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    assumeTrue(isLocalDevelopment());
    assumeTrue(shouldRunSlowTests());

    KeepRuleConsumer keepRuleConsumer = new PresentKeepRuleConsumer();
    R8TestCompileResult r8CompileResult = compileApplicationWithR8(keepRuleConsumer);
    L8TestCompileResult l8CompileResult = compileDesugaredLibraryWithL8(keepRuleConsumer);

    inspect(r8CompileResult, l8CompileResult);

    if (isLocalDevelopment()) {
      dump(r8CompileResult, l8CompileResult);
    }
  }

  @Test
  public void testProtoRewriting() throws Exception {
    assumeTrue(shouldRunSlowTests());

    StringConsumer keepRuleConsumer = StringConsumer.emptyConsumer();
    R8TestCompileResult r8CompileResult =
        compileApplicationWithR8(
            keepRuleConsumer,
            builder ->
                builder
                    .addKeepRules(
                        keepAllProtosRule(),
                        keepDynamicMethodSignatureRule(),
                        keepNewMessageInfoSignatureRule())
                    .allowCheckDiscardedErrors(true));
    assertRewrittenProtoSchemasMatch(
        new CodeInspector(getProgramFiles()), r8CompileResult.inspector());
  }

  @After
  public void teardown() {
    reporter.failIfPendingErrors();
  }

  private R8TestCompileResult compileApplicationWithR8(StringConsumer keepRuleConsumer)
      throws IOException, CompilationFailedException {
    return compileApplicationWithR8(keepRuleConsumer, ThrowableConsumer.empty());
  }

  private R8TestCompileResult compileApplicationWithR8(
      StringConsumer keepRuleConsumer, ThrowableConsumer<R8FullTestBuilder> configuration)
      throws IOException, CompilationFailedException {
    return testForR8(parameters.getBackend())
        .addProgramFiles(getProgramFiles())
        .addLibraryFiles(getLibraryFiles())
        .addKeepRuleFiles(getKeepRuleFiles())
        .addDontWarn("android.app.Activity$TranslucentConversionListener")
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .apply(configuration)
        .setMinApi(getApiLevel())
        .enableCoreLibraryDesugaring(
            getApiLevel(),
            keepRuleConsumer,
            StringResource.fromFile(getDesugaredLibraryConfiguration()))
        .compile()
        .assertAllInfoMessagesMatch(
            anyOf(
                containsString("Ignoring option: -optimizations"),
                containsString("Proguard configuration rule does not match anything"),
                containsString("Invalid signature")))
        .apply(this::printProtoStats);
  }

  private L8TestCompileResult compileDesugaredLibraryWithL8(KeepRuleConsumer keepRuleConsumer)
      throws CompilationFailedException, IOException, ExecutionException {
    return testForL8(getApiLevel())
        .setDesugaredLibraryConfiguration(getDesugaredLibraryConfiguration())
        .setDesugarJDKLibs(getDesugaredLibraryJDKLibs())
        .setDesugarJDKLibsConfiguration(getDesugaredLibraryJDKLibsConfiguration())
        .addGeneratedKeepRules(keepRuleConsumer.get())
        .addKeepRuleFiles(getDesugaredLibraryKeepRuleFiles())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .compile();
  }

  private void inspect(R8TestCompileResult r8CompileResult, L8TestCompileResult l8CompileResult)
      throws IOException, ResourceException {
    r8CompileResult.runDex2Oat(parameters.getRuntime()).assertNoVerificationErrors();
    l8CompileResult.runDex2Oat(parameters.getRuntime()).assertNoVerificationErrors();

    int applicationSize = r8CompileResult.getApp().applicationSize();
    if (applicationSize > MAX_APPLICATION_SIZE) {
      reporter.error(
          "Expected application size to be <="
              + MAX_APPLICATION_SIZE
              + ", but was "
              + applicationSize);
    }

    int desugaredLibrarySize = l8CompileResult.getApp().applicationSize();
    if (desugaredLibrarySize > MAX_DESUGARED_LIBRARY_SIZE) {
      reporter.error(
          "Expected desugared library size to be <="
              + MAX_DESUGARED_LIBRARY_SIZE
              + ", but was "
              + desugaredLibrarySize);
    }

    if (isLocalDevelopment()) {
      System.out.println("Dex size (application, excluding desugared library): " + applicationSize);
      System.out.println("Dex size (desugared library): " + desugaredLibrarySize);
      System.out.println("Dex size (total): " + (applicationSize + desugaredLibrarySize));
    }
  }

  private void dump(R8TestCompileResult r8CompileResult, L8TestCompileResult l8CompileResult)
      throws IOException {
    assertTrue(isLocalDevelopment());
    Files.createDirectories(dumpDirectory);
    r8CompileResult
        .writeToDirectory(dumpDirectory)
        .writeProguardMap(dumpDirectory.resolve("mapping.txt"));
    l8CompileResult
        .writeSingleDexOutputToFile(dumpDirectory.resolve("classes5.dex"))
        .writeGeneratedKeepRules(dumpDirectory.resolve("l8-keep.txt"))
        .writeProguardMap(dumpDirectory.resolve("l8-mapping.txt"));
  }
}
