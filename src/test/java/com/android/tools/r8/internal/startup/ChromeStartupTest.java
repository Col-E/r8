// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.startup;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ArchiveProgramResourceProvider;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ChromeStartupTest extends TestBase {

  private static AndroidApiLevel apiLevel = AndroidApiLevel.N;

  // Location of dump.zip and startup.txt.
  private static Path chromeDirectory = Paths.get("build/chrome/startup");

  // Location of test artifacts.
  private static Path artifactDirectory = Paths.get("build/chrome/startup");

  // Temporary directory where dump.zip is extracted into.
  private static Path dumpDirectory;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @BeforeClass
  public static void setup() throws IOException {
    assumeTrue(ToolHelper.isLocalDevelopment());
    dumpDirectory = getStaticTemp().newFolder().toPath();
    ZipUtils.unzip(chromeDirectory.resolve("dump.zip"), dumpDirectory);
  }

  // Outputs the instrumented dex in chrome/instrumented.
  @Test
  public void buildInstrumentedDex() throws Exception {
    buildInstrumentedBase();
    buildInstrumentedChromeSplit();
  }

  private void buildInstrumentedBase() throws Exception {
    Files.createDirectories(artifactDirectory.resolve("instrumented/base/dex"));
    testForD8()
        .addProgramFiles(dumpDirectory.resolve("program.jar"))
        .addClasspathFiles(dumpDirectory.resolve("classpath.jar"))
        .addLibraryFiles(dumpDirectory.resolve("library.jar"))
        .addOptionsModification(
            options ->
                options
                    .getStartupInstrumentationOptions()
                    .setEnableStartupInstrumentation()
                    .setStartupInstrumentationTag("r8"))
        .setMinApi(apiLevel)
        .release()
        .compile()
        .writeToDirectory(artifactDirectory.resolve("instrumented/base/dex"));
  }

  private void buildInstrumentedChromeSplit() throws Exception {
    Files.createDirectories(artifactDirectory.resolve("instrumented/chrome/dex"));
    testForD8()
        // The Chrome split is the feature that contains ChromeApplicationImpl.
        .addProgramFiles(getChromeSplit())
        .addClasspathFiles(dumpDirectory.resolve("program.jar"))
        .addClasspathFiles(dumpDirectory.resolve("classpath.jar"))
        .addLibraryFiles(dumpDirectory.resolve("library.jar"))
        .addOptionsModification(
            options ->
                options
                    .getStartupInstrumentationOptions()
                    .setEnableStartupInstrumentation()
                    .setStartupInstrumentationTag("r8"))
        .setMinApi(apiLevel)
        .release()
        .compile()
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz("org.chromium.chrome.browser.ChromeApplicationImpl"),
                    isPresent()))
        .writeToDirectory(artifactDirectory.resolve("instrumented/chrome/dex"));
  }

  private Path getChromeSplit() {
    return getFeatureSplit(9);
  }

  private Path getFeatureSplit(int index) {
    return dumpDirectory.resolve("feature-" + index + ".jar");
  }

  // Outputs Chrome built using R8 in chrome/default.
  @Test
  public void buildR8Default() throws Exception {
    buildR8(ThrowableConsumer.empty(), artifactDirectory.resolve("default"));
  }

  // Outputs Chrome built using R8 with limited class merging in chrome/default-with-patches.
  @Test
  public void buildR8DefaultWithPatches() throws Exception {
    buildR8(
        testBuilder ->
            testBuilder.addOptionsModification(
                options -> options.horizontalClassMergerOptions().setEnableSameFilePolicy(true)),
        artifactDirectory.resolve("default-with-patches"));
  }

  // Outputs Chrome built using R8 with minimal startup dex and no boundary optimizations in
  // chrome/optimized-minimal-nooptimize.
  @Test
  public void buildR8MinimalStartupDexWithoutBoundaryOptimizations() throws Exception {
    boolean enableMinimalStartupDex = true;
    boolean enableStartupBoundaryOptimizations = false;
    buildR8Startup(
        enableMinimalStartupDex,
        enableStartupBoundaryOptimizations,
        artifactDirectory.resolve("optimized-minimal-nooptimize"));
  }

  // Outputs Chrome built using R8 with minimal startup dex and boundary optimizations enabled in
  // chrome/optimized-minimal-optimize.
  @Test
  public void buildR8MinimalStartupDexWithBoundaryOptimizations() throws Exception {
    boolean enableMinimalStartupDex = true;
    boolean enableStartupBoundaryOptimizations = true;
    buildR8Startup(
        enableMinimalStartupDex,
        enableStartupBoundaryOptimizations,
        artifactDirectory.resolve("optimized-minimal-optimize"));
  }

  // Outputs Chrome built using R8 with startup layout enabled and no boundary optimizations in
  // chrome/optimized-nominimal-nooptimize.
  @Test
  public void buildR8StartupLayoutWithoutBoundaryOptimizations() throws Exception {
    boolean enableMinimalStartupDex = false;
    boolean enableStartupBoundaryOptimizations = false;
    buildR8Startup(
        enableMinimalStartupDex,
        enableStartupBoundaryOptimizations,
        artifactDirectory.resolve("optimized-nominimal-nooptimize"));
  }

  // Outputs Chrome built using R8 with startup layout enabled and no boundary optimizations in
  // chrome/optimized-nominimal-optimize.
  @Test
  public void buildR8StartupLayoutWithBoundaryOptimizations() throws Exception {
    boolean enableMinimalStartupDex = false;
    boolean enableStartupBoundaryOptimizations = true;
    buildR8Startup(
        enableMinimalStartupDex,
        enableStartupBoundaryOptimizations,
        artifactDirectory.resolve("optimized-nominimal-optimize"));
  }

  private void buildR8(ThrowableConsumer<R8FullTestBuilder> configuration, Path outDirectory)
      throws Exception {
    Files.createDirectories(outDirectory.resolve("base/dex"));
    Files.createDirectories(outDirectory.resolve("chrome/dex"));
    testForR8(Backend.DEX)
        .addProgramFiles(dumpDirectory.resolve("program.jar"))
        .addClasspathFiles(dumpDirectory.resolve("classpath.jar"))
        .addLibraryFiles(dumpDirectory.resolve("library.jar"))
        .addKeepRuleFiles(dumpDirectory.resolve("proguard.config"))
        .apply(
            testBuilder -> {
              int i = 1;
              boolean seenChromeSplit = false;
              for (; i <= 12; i++) {
                Path feature = getFeatureSplit(i);
                boolean isChromeSplit = feature.equals(getChromeSplit());
                seenChromeSplit |= isChromeSplit;
                assertTrue(feature.toFile().exists());
                testBuilder.addFeatureSplit(
                    featureSplitBuilder ->
                        featureSplitBuilder
                            .addProgramResourceProvider(
                                ArchiveProgramResourceProvider.fromArchive(feature))
                            .setProgramConsumer(
                                isChromeSplit
                                    ? new DexIndexedConsumer.DirectoryConsumer(
                                        outDirectory.resolve("chrome/dex"))
                                    : DexIndexedConsumer.emptyConsumer())
                            .build());
              }
              assertFalse(dumpDirectory.resolve("feature-" + i + ".jar").toFile().exists());
              assertTrue(seenChromeSplit);
              assertThat(
                  new CodeInspector(getChromeSplit())
                      .clazz("org.chromium.chrome.browser.ChromeApplicationImpl"),
                  isPresent());
            })
        .apply(configuration)
        .apply(this::disableR8StrictMode)
        .apply(this::disableR8TestingDefaults)
        .setMinApi(apiLevel)
        .compile()
        .writeToDirectory(outDirectory.resolve("base/dex"));
  }

  private void buildR8Startup(
      boolean enableMinimalStartupDex,
      boolean enableStartupBoundaryOptimizations,
      Path outDirectory)
      throws Exception {
    StartupProfileProvider startupProfileProvider =
        new StartupProfileProvider() {
          @Override
          public String get() {
            return StringResource.fromFile(chromeDirectory.resolve("startup.txt"))
                .getStringWithRuntimeException();
          }

          @Override
          public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
            throw new Unimplemented();
          }

          @Override
          public Origin getOrigin() {
            return Origin.unknown();
          }
        };

    buildR8(
        testBuilder ->
            testBuilder.addOptionsModification(
                options ->
                    options
                        .getStartupOptions()
                        .setEnableMinimalStartupDex(enableMinimalStartupDex)
                        .setEnableStartupBoundaryOptimizations(enableStartupBoundaryOptimizations)
                        .setStartupProfileProvider(startupProfileProvider)),
        outDirectory);
  }

  private void disableR8StrictMode(R8FullTestBuilder testBuilder) {
    testBuilder
        .allowDiagnosticMessages()
        .allowUnnecessaryDontWarnWildcards()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces());
  }

  private void disableR8TestingDefaults(R8FullTestBuilder testBuilder) {
    testBuilder.addOptionsModification(
        options -> options.horizontalClassMergerOptions().setEnableInterfaceMerging(false));
  }
}
