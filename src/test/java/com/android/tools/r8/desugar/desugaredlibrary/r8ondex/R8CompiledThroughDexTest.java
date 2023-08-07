// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.r8ondex;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DeterminismChecker;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8CompiledThroughDexTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    // We only run this test with ART 8 and with full desugaring to avoid the large runtime on ART.
    return buildParameters(
        getTestParameters()
            // Android 5 and 6 do not seem to work with more than 512 Mb of RAM.
            .withDexRuntime(Version.V8_1_0)
            // TODO(b/241748003): Broken strictly below 19 due to charset issue.
            .withApiLevel(AndroidApiLevel.N)
            .build(),
        ImmutableList.of(JDK11_PATH),
        ImmutableList.of(D8_L8DEBUG));
  }

  public R8CompiledThroughDexTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  private static String commandLinePathFor(String string) {
    return commandLinePathFor(Paths.get(string));
  }

  private static String commandLinePathFor(Path path) {
    // We switch to absolute path due to the art frameworks requiring to run the command in a
    // different folder.
    return path.toAbsolutePath().toString();
  }

  private static final String R8_KEEP =
      Paths.get(ToolHelper.SOURCE_DIR + "main/keep.txt").toAbsolutePath().toString();

  private Pair<List<String>, Consumer<Builder>> buildArguments() {
    ImmutableList.Builder<String> arguments = ImmutableList.builder();
    List<Consumer<Builder>> buildup = new ArrayList<>();

    arguments.add(commandLinePathFor(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR));
    buildup.add(b -> b.addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR));

    arguments.add("--release");
    buildup.add(b -> b.setMode(CompilationMode.RELEASE));

    arguments.add("--min-api").add(Integer.toString(parameters.getApiLevel().getLevel()));
    buildup.add(b -> b.setMinApiLevel(parameters.getApiLevel().getLevel()));

    arguments.add("--lib").add(commandLinePathFor(ToolHelper.getAndroidJar(AndroidApiLevel.R)));
    buildup.add(b -> b.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.R)));

    arguments.add("--pg-conf").add(commandLinePathFor(R8_KEEP));
    buildup.add(b -> b.addProguardConfigurationFiles(Paths.get(R8_KEEP)));

    arguments
        .add("--desugared-lib")
        .add(commandLinePathFor(libraryDesugaringSpecification.getSpecification()));
    buildup.add(
        b ->
            b.addDesugaredLibraryConfiguration(
                StringResource.fromFile(libraryDesugaringSpecification.getSpecification())));

    if (!compilationSpecification.isProgramShrink()) {
      arguments.add("--no-minification");
      buildup.add(b -> b.setDisableMinification(true));
    }

    Consumer<Builder> consumer = b -> {};
    for (Consumer<Builder> step : buildup) {
      consumer = consumer.andThen(step);
    }

    return new Pair<>(arguments.build(), consumer);
  }

  private List<String> getSharedArguments() {
    return buildArguments().getFirst();
  }

  private Consumer<Builder> getSharedBuilder() {
    return buildArguments().getSecond();
  }

  private void printTime(String title, long start) {
    System.out.println(title + ": " + ((System.nanoTime() - start) / 1000000000) + "s");
  }

  @Test
  public void testR8CompiledWithR8Dex() throws Exception {
    // Compile once R8_WITH_RELOCATED_DEPS_JAR using normal R8_WITH_RELOCATED_DEPS_JAR to dex,
    // and once R8_WITH_RELOCATED_DEPS_JAR with the previously compiled version to dex.
    // Both applications should be identical.

    // We use runJava and runArtRaw to show explicitly we run the exact same commands.
    // We use extra VM parameters for memory. The command parameters should look like:
    // -Xmx512m com...R8 --release --min-api 1 --output path/to/folder --lib rt.jar
    // --pg-conf R8KeepRules r8.jar
    // The 512m memory is required to run on ART but any higher and the runtime will fail too.

    Path r8jar = ToolHelper.R8_WITH_RELOCATED_DEPS_JAR;
    Path outputFolder = temp.newFolder("output").toPath();
    Path outputThroughCf = outputFolder.resolve("outThroughCf.zip");
    Path outputThroughCfExternal = outputFolder.resolve("outThroughCf_external.zip");
    Path determinismLogsDir = temp.newFolder("logs").toPath();

    // First run compiles with R8 externally.
    runExternalR8(r8jar, outputThroughCfExternal, determinismLogsDir);

    // Second run compiles with R8 in process checking it to be equal with the first compilation.
    // If this fails, likely due to non-determinism, then that is much faster than waiting for the
    // Art run compilation to finish if the same error can be found directly.
    // Run the in-process second to allow hitting the in-process breakpoint on failure.
    runInProcessR8(outputThroughCf, determinismLogsDir);

    // Check the outputs are identical.
    {
      String message =
          "The output of R8/JVM in-process and R8/JVM external differ."
              + " Make sure you have an up-to-date compilation of "
              + r8jar
              + ". If not, that could very likely cause the in-process run (eg, via intellij) to"
              + " differ from the external run which uses "
              + r8jar
              + ". If up-to-date, the likely cause of this error is that R8 is non-deterministic.";
      uploadJarsToCloudStorageIfTestFails(
          TestBase::filesAreEqual, outputThroughCf, outputThroughCfExternal);
      assertProgramsEqual(message, outputThroughCf, outputThroughCfExternal);
    }

    // Check that setting the determinism checker wrote a log file.
    assertTrue(Files.exists(determinismLogsDir.resolve("0.log")));

    // Finally compile R8 on the ART runtime using the already compiled DEX version of R8.
    // We need the extra parameter --64 to use 64 bits frameworks.
    {
      long start = System.nanoTime();
      Path outputThroughDex = outputFolder.resolve("outThroughDex.zip");
      ProcessResult artProcessResult =
          ToolHelper.runArtRaw(
              ImmutableList.of(
                  commandLinePathFor(outputThroughCf),
                  commandLinePathFor(
                      getNonShrunkDesugaredLib(parameters, libraryDesugaringSpecification))),
              R8.class.getTypeName(),
              builder -> builder.appendArtOption("--64").appendArtOption("-Xmx512m"),
              parameters.getRuntime().asDex().getVm(),
              null,
              ImmutableList.builder()
                  .add("--output")
                  .add(commandLinePathFor(outputThroughDex))
                  .addAll(getSharedArguments())
                  .build()
                  .toArray(new String[0]));
      printTime("R8/ART", start);
      if (artProcessResult.exitCode != 0) {
        System.out.println(artProcessResult);
      }
      assertEquals(artProcessResult.stderr, 0, artProcessResult.exitCode);
      assertProgramsEqual(
          "The output of R8/JVM in-process and R8/ART external differ.",
          outputThroughCf,
          outputThroughDex);
    }
  }

  private void runExternalR8(Path r8jar, Path outputThroughCfExternal, Path determinismLogsDir)
      throws IOException {
    String checkDeterminismKey = "com.android.tools.r8.checkdeterminism";
    long start = System.nanoTime();
    ProcessResult javaProcessResult =
        ToolHelper.runJava(
            TestRuntime.getSystemRuntime(),
            Collections.singletonList(r8jar),
            ImmutableList.builder()
                .add("-Xmx1g")
                // Set the system property to check determinism.
                .add("-D" + checkDeterminismKey + "=" + determinismLogsDir)
                .add(R8.class.getTypeName())
                .add("--output")
                .add(commandLinePathFor(outputThroughCfExternal))
                .addAll(getSharedArguments())
                .build()
                .toArray(new String[0]));
    printTime("R8/JVM external", start);
    assertEquals(javaProcessResult.toString(), 0, javaProcessResult.exitCode);
  }

  private void runInProcessR8(Path outputThroughCf, Path determinismLogsDir)
      throws CompilationFailedException {
    long start = System.nanoTime();
    // Manually construct the R8 command as the test builder will change defaults compared
    // to the CLI invocation (eg, compressed and pg-map output).
    Builder builder = R8Command.builder().setOutput(outputThroughCf, OutputMode.DexIndexed);
    // Set API model until default changes.
    builder.setEnableExperimentalMissingLibraryApiModeling(true);
    getSharedBuilder().accept(builder);
    ToolHelper.runR8WithOptionsModificationOnly(
        builder.build(),
        o ->
            o.testing.setDeterminismChecker(
                DeterminismChecker.createWithFileBacking(determinismLogsDir)));
    printTime("R8/JVM in-process", start);
  }
}
