// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.L8TestCompileResult;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.tracereferences.TraceReferences;
import com.android.tools.r8.tracereferences.TraceReferencesCommand;
import com.android.tools.r8.tracereferences.TraceReferencesKeepRules;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class YouTubeV1719Test extends YouTubeCompilationTestBase {

  private static final int MAX_APPLICATION_SIZE = 29750000;
  private static final int MAX_DESUGARED_LIBRARY_SIZE = 425000;

  private final TestParameters parameters;

  // Location where test artifacts will be dumped when `local_development` is set.
  private final Path dumpDirectory = Paths.get("YouTubeV1719-" + System.currentTimeMillis());

  // By setting this to an actual startup list, YouTube will be build with layout optimizations
  // enabled.
  private final StartupProfileProvider startupProfileProvider = null;
  private final boolean enableMinimalStartupDex = true;
  private final boolean enableStartupBoundaryOptimizations = false;

  private final Reporter reporter = new Reporter();

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.M).build();
  }

  public YouTubeV1719Test(TestParameters parameters) {
    super(17, 19, parameters.getApiLevel());
    this.parameters = parameters;
  }

  /**
   * Running this test will dump an instrumented version of YouTube in the {@link #dumpDirectory}.
   */
  @Test
  public void testStartupInstrumentation() throws Exception {
    assumeTrue(isLocalDevelopment());
    assumeTrue(shouldRunSlowTests());

    // Compile the app with instrumentation and core library desugaring enabled.
    D8TestCompileResult d8CompileResult =
        testForD8()
            .addProgramFiles(getProgramFiles())
            .addLibraryFiles(getLibraryFileWithoutDesugaredLibrary())
            .addOptionsModification(
                options ->
                    options
                        .getStartupInstrumentationOptions()
                        .setEnableStartupInstrumentation()
                        .setStartupInstrumentationTag("r8"))
            .enableCoreLibraryDesugaring(
                LibraryDesugaringTestConfiguration.builder()
                    .addDesugaredLibraryConfiguration(
                        StringResource.fromFile(getDesugaredLibraryConfiguration()))
                    .build())
            .release()
            .setMinApi(parameters)
            .compile();

    // Compile desugared library using cf backend (without keep rules).
    L8TestCompileResult l8CompileResult = compileDesugaredLibraryWithL8();

    // Generate keep rules for desugared library using trace references.
    Path generatedKeepRules = temp.newFile().toPath();
    TraceReferences.run(
        TraceReferencesCommand.builder()
            .addLibraryFiles(getLibraryFileWithoutDesugaredLibrary())
            .addSourceFiles(d8CompileResult.writeToZip())
            .addTargetFiles(l8CompileResult.writeToZip())
            .setConsumer(
                TraceReferencesKeepRules.builder()
                    .setAllowObfuscation(false)
                    .setOutputConsumer(new FileConsumer(generatedKeepRules))
                    .build())
            .build());

    // Compile desugared library using the generated keep rules.
    R8TestCompileResult l8R8CompileResult =
        compileDesugaredLibraryWithR8(l8CompileResult, generatedKeepRules);

    if (isLocalDevelopment()) {
      dump(d8CompileResult, l8R8CompileResult);
    }
  }

  /**
   * Running this test will dump an R8 build of YouTube in the {@link #dumpDirectory}, where the
   * desugared library keep rules are generated using trace references.
   *
   * <p>If {@link #startupProfileProvider} is set to a concrete startup list, YouTube will be build
   * with layout optimizations enabled.
   */
  @Test
  public void testR8() throws Exception {
    assumeTrue(isLocalDevelopment());
    assumeTrue(shouldRunSlowTests());

    // Compile app using R8, passing the startup list.
    R8TestCompileResult r8CompileResult =
        compileApplicationWithR8(
            testBuilder ->
                testBuilder
                    .addOptionsModification(
                        options -> {
                          if (startupProfileProvider != null) {
                            options
                                .getStartupOptions()
                                .setEnableMinimalStartupDex(enableMinimalStartupDex)
                                .setEnableStartupBoundaryOptimizations(
                                    enableStartupBoundaryOptimizations);
                          }
                        })
                    .applyIf(
                        startupProfileProvider != null,
                        b -> b.addStartupProfileProviders(startupProfileProvider)));

    // Compile desugared library using cf backend (without keep rules).
    L8TestCompileResult l8CompileResult = compileDesugaredLibraryWithL8();

    // Generate keep rules for desugared library using trace references.
    Path generatedKeepRules = temp.newFile().toPath();
    TraceReferences.run(
        TraceReferencesCommand.builder()
            .addLibraryFiles(getLibraryFileWithoutDesugaredLibrary())
            .addSourceFiles(r8CompileResult.writeToZip())
            .addTargetFiles(l8CompileResult.writeToZip())
            .setConsumer(
                TraceReferencesKeepRules.builder()
                    .setAllowObfuscation(false)
                    .setOutputConsumer(new FileConsumer(generatedKeepRules))
                    .build())
            .build());

    // Compile desugared library using the generated keep rules.
    R8TestCompileResult l8R8CompileResult =
        compileDesugaredLibraryWithR8(l8CompileResult, generatedKeepRules);

    if (isLocalDevelopment()) {
      dump(r8CompileResult, l8R8CompileResult);
    }

    inspect(r8CompileResult, l8CompileResult);
  }

  /**
   * Validates that when all protos are kept and the proto optmization is enabled, the generated
   * proto schemas are identical to the proto schemas in the input.
   */
  @Ignore
  @Test
  public void testProtoRewriting() throws Exception {
    assumeTrue(shouldRunSlowTests());

    R8TestCompileResult r8CompileResult =
        compileApplicationWithR8(
            builder ->
                builder
                    .addKeepRules(
                        keepAllProtosRule(),
                        keepDynamicMethodSignatureRule(),
                        keepNewMessageInfoSignatureRule())
                    .allowCheckDiscardedErrors());
    assertRewrittenProtoSchemasMatch(
        new CodeInspector(getProgramFiles()), r8CompileResult.inspector());
  }

  @After
  public void teardown() {
    reporter.failIfPendingErrors();
  }

  private R8TestCompileResult compileApplicationWithR8(
      ThrowableConsumer<R8FullTestBuilder> configuration)
      throws IOException, CompilationFailedException {
    return testForR8(parameters.getBackend())
        .addProgramFiles(getProgramFiles())
        .addLibraryFiles(getLibraryFileWithoutDesugaredLibrary())
        .addKeepRuleFiles(getKeepRuleFiles())
        .addDontWarn("android.app.Activity$TranslucentConversionListener")
        .apply(configuration)
        .apply(this::disableR8StrictMode)
        .apply(this::disableR8TestingDefaults)
        .setMinApi(getApiLevel())
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.builder()
                .addDesugaredLibraryConfiguration(
                    StringResource.fromFile(getDesugaredLibraryConfiguration()))
                .build())
        .compile()
        .assertAllInfoMessagesMatch(
            anyOf(
                containsString("Ignoring option: -optimizations"),
                containsString("Proguard configuration rule does not match anything"),
                containsString("Invalid signature")))
        .apply(this::printProtoStats);
  }

  private L8TestCompileResult compileDesugaredLibraryWithL8()
      throws CompilationFailedException, IOException, ExecutionException {
    return testForL8(getApiLevel(), Backend.CF)
        .setDesugaredLibrarySpecification(getDesugaredLibraryConfiguration())
        .addProgramFiles(getDesugaredLibraryJDKLibs())
        .addLibraryFiles(getLibraryFileWithoutDesugaredLibrary())
        .compile();
  }

  private R8TestCompileResult compileDesugaredLibraryWithR8(
      L8TestCompileResult l8CompileResult, Path generatedKeepRules)
      throws IOException, CompilationFailedException {
    return testForR8(parameters.getBackend())
        .addProgramFiles(l8CompileResult.writeToZip())
        .addLibraryFiles(getLibraryFileWithoutDesugaredLibrary())
        .addKeepRuleFiles(getDesugaredLibraryKeepRuleFiles(generatedKeepRules))
        .apply(this::disableR8StrictMode)
        .apply(this::disableR8TestingDefaults)
        .setMinApi(parameters)
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

  private void dump(TestCompileResult<?, ?> compileResult, R8TestCompileResult l8R8CompileResult)
      throws IOException {
    assertTrue(isLocalDevelopment());
    Files.createDirectories(dumpDirectory);

    // Dump dex.
    compileResult.writeToDirectory(dumpDirectory);

    // Dump mapping.
    if (compileResult instanceof R8TestCompileResult) {
      R8TestCompileResult r8TestCompileResult = (R8TestCompileResult) compileResult;
      r8TestCompileResult.writeProguardMap(dumpDirectory.resolve("mapping.txt"));
    }

    // Dump desugared library dex.
    int i = 2;
    while (true) {
      Path desugaredLibraryDexFile = dumpDirectory.resolve("classes" + i + ".dex");
      if (!desugaredLibraryDexFile.toFile().exists()) {
        l8R8CompileResult.writeSingleDexOutputToFile(desugaredLibraryDexFile);
        break;
      }
      i++;
    }

    // Dump desugared library mapping.
    l8R8CompileResult.writeProguardMap(dumpDirectory.resolve("mapping-desugared-library.txt"));
  }

  private void disableR8StrictMode(R8FullTestBuilder testBuilder) {
    testBuilder
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules();
  }

  private void disableR8TestingDefaults(R8FullTestBuilder testBuilder) {
    testBuilder.addOptionsModification(
        options -> options.horizontalClassMergerOptions().setEnableInterfaceMerging(false));
  }
}
