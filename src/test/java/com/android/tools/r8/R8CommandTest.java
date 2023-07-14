// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.ToolHelper.EXAMPLES_BUILD_DIR;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.AssertionsConfiguration.AssertionTransformationScope;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.origin.EmbeddedOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ExtractMarkerUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.ApiModelTestingOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8CommandTest extends CommandTestBase<R8Command> {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public R8CommandTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  static class A {}

  static class B {}

  private Path getJarWithA() throws Exception {
    Path jar = temp.newFolder().toPath().resolve("out.jar");
    writeClassesToJar(jar, A.class);
    return jar;
  }

  private Path getJarWithB() throws Exception {
    Path jar = temp.newFolder().toPath().resolve("out.jar");
    writeClassesToJar(jar, B.class);
    return jar;
  }

  @Test(expected = CompilationFailedException.class)
  public void emptyBuilder() throws Throwable {
    // The builder must have a program consumer.
    R8Command.builder().build();
  }

  @Test
  public void emptyCommand() throws Throwable {
    verifyEmptyCommand(
        // In the API we must set a consumer.
        R8Command.builder().setProgramConsumer(DexIndexedConsumer.emptyConsumer()).build());
    verifyEmptyCommand(parse());
    verifyEmptyCommand(parse(""));
    verifyEmptyCommand(parse("", ""));
    verifyEmptyCommand(parse(" "));
    verifyEmptyCommand(parse(" ", " "));
    verifyEmptyCommand(parse("\t"));
    verifyEmptyCommand(parse("\t", "\t"));
  }

  private void verifyEmptyCommand(R8Command command) throws Throwable {
    assertEquals(0, ToolHelper.getApp(command).getDexProgramResourcesForTesting().size());
    assertEquals(0, ToolHelper.getApp(command).getClassProgramResourcesForTesting().size());
    assertTrue(command.getEnableMinification());
    assertTrue(command.getEnableTreeShaking());
    assertEquals(CompilationMode.RELEASE, command.getMode());
    assertTrue(command.getProgramConsumer() instanceof DexIndexedConsumer);
    assertFalse(command.getProguardCompatibility());
  }

  @Test(expected = CompilationFailedException.class)
  public void disallowDexFilePerClassFileBuilder() throws Throwable {
    R8Command.builder().setProgramConsumer(DexFilePerClassFileConsumer.emptyConsumer()).build();
  }

  @Test
  public void allowClassFileConsumer() throws Throwable {
    assertTrue(
        R8Command.builder()
                .setProgramConsumer(ClassFileConsumer.emptyConsumer())
                .build()
                .getProgramConsumer()
            instanceof ClassFileConsumer);
  }

  @Test
  public void desugaredLibraryKeepRuleConsumer() throws Exception {
    StringConsumer stringConsumer = StringConsumer.emptyConsumer();
    D8Command command =
        D8Command.builder()
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .setDesugaredLibraryKeepRuleConsumer(stringConsumer)
            .build();
    assertSame(command.getInternalOptions().desugaredLibraryKeepRuleConsumer, stringConsumer);
  }

  @Test
  public void defaultOutIsCwd() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path input = getJarWithA();
    Path library = ToolHelper.getDefaultAndroidJar();
    Path output = working.resolve("classes.dex");
    assertFalse(Files.exists(output));
    ProcessResult result =
        ToolHelper.forkR8(
            working,
            input.toAbsolutePath().toString(),
            "--lib",
            library.toAbsolutePath().toString(),
            "--no-tree-shaking");
    assertEquals("R8 run failed: " + result.stderr, 0, result.exitCode);
    assertTrue(Files.exists(output));
  }

  @Test
  public void passFeatureSplit() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path input = getJarWithA();
    Path inputFeature = getJarWithB();
    Path library = ToolHelper.getDefaultAndroidJar();
    Path output = working.resolve("classes.dex");
    Path featureOutput = working.resolve("feature.zip");
    assertFalse(Files.exists(output));
    assertFalse(Files.exists(featureOutput));
    ProcessResult result =
        ToolHelper.forkR8(
            working,
            input.toAbsolutePath().toString(),
            "--lib",
            library.toAbsolutePath().toString(),
            "--feature",
            inputFeature.toAbsolutePath().toString(),
            featureOutput.toAbsolutePath().toString(),
            "--no-tree-shaking");
    assertEquals("R8 run failed: " + result.stderr, 0, result.exitCode);
    assertTrue(Files.exists(output));
    assertTrue(Files.exists(featureOutput));
  }

  @Test
  public void featureOnlyOneArgument() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path input = getJarWithA();
    Path inputFeature = getJarWithB();
    Path library = ToolHelper.getDefaultAndroidJar();
    Path output = working.resolve("classes.dex");
    assertFalse(Files.exists(output));
    ProcessResult result =
        ToolHelper.forkR8(
            working,
            input.toString(),
            "--lib",
            library.toAbsolutePath().toString(),
            "--no-tree-shaking",
            "--feature",
            inputFeature.toAbsolutePath().toString());
    assertNotEquals("R8 run failed: " + result.stderr, 0, result.exitCode);
    assertTrue(result.stderr.contains("Missing parameter for"));
  }

  @Test
  public void flagsFile() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path library = ToolHelper.getDefaultAndroidJar();
    Path input = getJarWithA();
    Path output = working.resolve("output.zip");
    Path flagsFile = working.resolve("flags.txt");
    FileUtils.writeTextFile(
        flagsFile,
        "--output",
        "output.zip",
        "--min-api",
        "24",
        "--lib",
        library.toAbsolutePath().toString(),
        "--no-tree-shaking",
        input.toAbsolutePath().toString());
    assertEquals(0, ToolHelper.forkR8(working, "@flags.txt").exitCode);
    assertTrue(Files.exists(output));
    Collection<Marker> markers = ExtractMarkerUtils.extractMarkersFromFile(output);
    assertEquals(1, markers.size());
    Marker marker = markers.iterator().next();
    assertEquals(24, marker.getMinApi().intValue());
    assertEquals(Tool.R8, marker.getTool());
  }

  @Test(expected=CompilationFailedException.class)
  public void nonExistingFlagsFile() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path flags = working.resolve("flags.txt").toAbsolutePath();
    assertNotEquals(0, ToolHelper.forkR8(working, "@flags.txt").exitCode);
    DiagnosticsChecker.checkErrorsContains(
        "NoSuchFileException",
        handler ->
            R8.run(
                R8Command.parse(
                        new String[] {"@" + flags.toString()}, EmbeddedOrigin.INSTANCE, handler)
                    .build()));
  }

  @Test(expected = CompilationFailedException.class)
  public void recursiveFlagsFile() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path flagsFile = working.resolve("flags.txt");
    Path recursiveFlagsFile = working.resolve("recursive_flags.txt");
    Path input = getJarWithA();
    FileUtils.writeTextFile(recursiveFlagsFile, "--output", "output.zip");
    FileUtils.writeTextFile(
        flagsFile, "--min-api", "24", input.toString(), "@" + recursiveFlagsFile);
    DiagnosticsChecker.checkErrorsContains(
        "Recursive @argfiles are not supported",
        handler ->
            R8.run(
                R8Command.parse(
                        new String[] {"@" + flagsFile.toString()}, EmbeddedOrigin.INSTANCE, handler)
                    .build()));
  }

  @Test
  public void printsHelpOnNoInput() throws Throwable {
    ProcessResult result = ToolHelper.forkR8(temp.getRoot().toPath());
    assertFalse(result.exitCode == 0);
    assertTrue(result.stderr.contains("Usage"));
    assertFalse(result.stderr.contains("R8_foobar")); // Sanity check
  }

  @Test
  public void validOutputPath() throws Throwable {
    Path existingDir = temp.getRoot().toPath();
    Path nonExistingZip = existingDir.resolve("a-non-existing-archive.zip");
    assertEquals(
        existingDir,
        getOutputPath(R8Command.builder().setOutput(existingDir, OutputMode.DexIndexed).build()));
    assertEquals(
        nonExistingZip,
        getOutputPath(
            R8Command.builder().setOutput(nonExistingZip, OutputMode.DexIndexed).build()));
    assertEquals(existingDir, getOutputPath(parse("--output", existingDir.toString())));
    assertEquals(nonExistingZip, getOutputPath(parse("--output", nonExistingZip.toString())));
  }

  static Path getOutputPath(BaseCompilerCommand command) {
    ProgramConsumer consumer = command.getProgramConsumer();
    if (consumer instanceof InternalProgramOutputPathConsumer) {
      return ((InternalProgramOutputPathConsumer) consumer).internalGetOutputPath();
    }
    return null;
  }

  @Test
  public void proguardCompatMode() throws Throwable {
    assertFalse(parse("").getProguardCompatibility());
    assertTrue(parse("--pg-compat").getProguardCompatibility());
  }

  @Test
  public void classFileOutputModeOption() throws Throwable {
    assertTrue(parse("--classfile").getProgramConsumer() instanceof ClassFileConsumer);
  }

  @Test
  public void classFileOutputModeAPI() throws Throwable {
    assertTrue(
        R8Command.builder()
                .setOutput(Paths.get("."), OutputMode.ClassFile)
                .build()
                .getProgramConsumer()
            instanceof ClassFileConsumer);
  }

  @Test
  public void mainDexRules() throws Throwable {
    Path mainDexRules1 = temp.newFile("main-dex-1.rules").toPath();
    Path mainDexRules2 = temp.newFile("main-dex-2.rules").toPath();
    parse("--main-dex-rules", mainDexRules1.toString());
    parse(
        "--main-dex-rules", mainDexRules1.toString(), "--main-dex-rules", mainDexRules2.toString());
  }

  @Test(expected = CompilationFailedException.class)
  public void nonExistingMainDexRules() throws Throwable {
    Path mainDexRules = temp.getRoot().toPath().resolve("main-dex.rules");
    parse("--main-dex-rules", mainDexRules.toString());
  }

  @Test
  public void mainDexList() throws Throwable {
    Path mainDexList1 = temp.newFile("main-dex-list-1.txt").toPath();
    Path mainDexList2 = temp.newFile("main-dex-list-2.txt").toPath();
    parse("--main-dex-list", mainDexList1.toString());
    parse("--main-dex-list", mainDexList1.toString(), "--main-dex-list", mainDexList2.toString());
  }

  @Test(expected = CompilationFailedException.class)
  public void nonExistingMainDexList() throws Throwable {
    Path mainDexList = temp.getRoot().toPath().resolve("main-dex-list.txt");
    parse("--main-dex-list", mainDexList.toString());
  }

  @Test
  public void mainDexListOutput() throws Throwable {
    Path mainDexRules = temp.newFile("main-dex.rules").toPath();
    Path mainDexList = temp.newFile("main-dex-list.txt").toPath();
    Path mainDexListOutput = temp.newFile("main-dex-out.txt").toPath();
    parse("--main-dex-rules", mainDexRules.toString(),
        "--main-dex-list-output", mainDexListOutput.toString());
    parse("--main-dex-list", mainDexList.toString(),
        "--main-dex-list-output", mainDexListOutput.toString());
  }

  @Test(expected = CompilationFailedException.class)
  public void mainDexListOutputWithoutAnyMainDexSpecification() throws Throwable {
    Path mainDexListOutput = temp.newFile("main-dex-out.txt").toPath();
    parse("--main-dex-list-output", mainDexListOutput.toString());
  }

  @Test(expected = CompilationFailedException.class)
  public void mainDexRulesWithNonLegacyMinApi() throws Throwable {
    Path mainDexRules = temp.newFile("main-dex.rules").toPath();
    DiagnosticsChecker.checkErrorsContains(
        "does not support main-dex",
        (handler) ->
            R8Command.builder(handler)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .setMinApiLevel(AndroidApiLevel.L.getLevel())
                .addMainDexRulesFiles(mainDexRules)
                .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void mainDexListWithNonLegacyMinApi() throws Throwable {
    Path mainDexList = temp.newFile("main-dex-list.txt").toPath();
    DiagnosticsChecker.checkErrorsContains(
        "does not support main-dex",
        (handler) ->
            R8Command.builder(handler)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .setMinApiLevel(AndroidApiLevel.L.getLevel())
                .addMainDexListFiles(mainDexList)
                .build());
  }

  @Test
  public void existingOutputDirWithDexFiles() throws Throwable {
    Path existingDir = temp.newFolder().toPath();
    List<Path> classesFiles = ImmutableList.of(
        existingDir.resolve("classes.dex"),
        existingDir.resolve("classes2.dex"),
        existingDir.resolve("Classes3.dex"), // ignore case.
        existingDir.resolve("classes10.dex"),
        existingDir.resolve("classes999.dex"));
    List<Path> otherFiles = ImmutableList.of(
        existingDir.resolve("classes0.dex"),
        existingDir.resolve("classes1.dex"),
        existingDir.resolve("classes010.dex"),
        existingDir.resolve("classesN.dex"),
        existingDir.resolve("other.dex"));
    for (Path file : classesFiles) {
      Files.createFile(file);
      assertTrue(Files.exists(file));
    }
    for (Path file : otherFiles) {
      Files.createFile(file);
      assertTrue(Files.exists(file));
    }
    Path input = getJarWithA();
    ProcessResult result =
        ToolHelper.forkR8(
            Paths.get("."),
            "--no-tree-shaking",
            "--no-minification",
            input.toString(),
            "--output",
            existingDir.toString(),
            "--lib",
            ToolHelper.getDefaultAndroidJar().toString());
    assertEquals(0, result.exitCode);
    assertTrue(Files.exists(classesFiles.get(0)));
    for (int i = 1; i < classesFiles.size(); i++) {
      Path file = classesFiles.get(i);
      assertFalse("Expected stale file to be gone: " + file, Files.exists(file));
    }
    for (Path file : otherFiles) {
      assertTrue("Expected non-classes file to remain: " + file, Files.exists(file));
    }
  }

  @Test(expected = CompilationFailedException.class)
  public void nonExistingOutputDir() throws Throwable {
    Path nonExistingDir = temp.getRoot().toPath().resolve("a/path/that/does/not/exist");
    R8Command.builder().setOutput(nonExistingDir, OutputMode.DexIndexed).build();
  }

  @Test
  public void existingOutputZip() throws Throwable {
    Path existingZip = temp.newFile("an-existing-archive.zip").toPath();
    R8Command.builder().setOutput(existingZip, OutputMode.DexIndexed).build();
  }

  @Test(expected = CompilationFailedException.class)
  public void invalidOutputFileType() throws Throwable {
    Path invalidType = temp.getRoot().toPath().resolve("an-invalid-output-file-type.foobar");
    R8Command.builder().setOutput(invalidType, OutputMode.DexIndexed).build();
  }

  @Test(expected = CompilationFailedException.class)
  public void nonExistingOutputDirParse() throws Throwable {
    Path nonExistingDir = temp.getRoot().toPath().resolve("a/path/that/does/not/exist");
    parse("--output", nonExistingDir.toString());
  }

  @Test
  public void existingOutputZipParse() throws Throwable {
    Path existingZip = temp.newFile("an-existing-archive.zip").toPath();
    parse("--output", existingZip.toString());
  }

  @Test(expected = CompilationFailedException.class)
  public void invalidOutputFileTypeParse() throws Throwable {
    Path invalidType = temp.getRoot().toPath().resolve("an-invalid-output-file-type.foobar");
    parse("--output", invalidType.toString());
  }

  @Test
  public void nonExistingOutputJar() throws Throwable {
    Path nonExistingJar = temp.getRoot().toPath().resolve("non-existing-archive.jar");
    R8Command.builder().setOutput(nonExistingJar, OutputMode.DexIndexed).build();
  }

  @Test(expected = CompilationFailedException.class)
  public void dexFileUnsupported() throws Throwable {
    Path dexFile = temp.newFile("test.dex").toPath();
    DiagnosticsChecker.checkErrorsContains("DEX input", handler ->
        R8Command
            .builder(handler)
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .addProgramFiles(dexFile)
            .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void dexProviderUnsupported() throws Throwable {
    Path dexFile = temp.newFile("test.dex").toPath();
    DiagnosticsChecker.checkErrorsContains(
        "DEX input",
        handler ->
            R8.run(
                R8Command.builder(handler)
                    .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                    .addProgramResourceProvider(
                        new ProgramResourceProvider() {
                          @Override
                          public Collection<ProgramResource> getProgramResources() {
                            return Collections.singleton(
                                ProgramResource.fromFile(Kind.DEX, dexFile));
                          }
                        })
                    .build()));
  }

  @Test
  public void dexDataUnsupported() {
    for (Method method : R8Command.Builder.class.getMethods()) {
      assertNotEquals("addDexProgramData", method.getName());
    }
  }

  @Test(expected = CompilationFailedException.class)
  public void vdexFileUnsupported() throws Throwable {
    Path vdexFile = temp.newFile("test.vdex").toPath();
    R8Command.builder()
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
        .addProgramFiles(vdexFile)
        .build();
  }

  @Test(expected = CompilationFailedException.class)
  public void duplicateApiLevel() throws CompilationFailedException {
    DiagnosticsChecker.checkErrorsContains(
        "multiple --min-api", handler -> parse(handler, "--min-api", "19", "--min-api", "21"));
  }

  @Test(expected = CompilationFailedException.class)
  public void invalidApiLevel() throws CompilationFailedException {
    DiagnosticsChecker.checkErrorsContains(
        "Invalid argument to --min-api", handler -> parse(handler, "--min-api", "foobar"));
  }

  @Test(expected = CompilationFailedException.class)
  public void negativeApiLevel() throws CompilationFailedException {
    DiagnosticsChecker.checkErrorsContains(
        "Invalid argument to --min-api", handler -> parse(handler, "--min-api", "-21"));
  }

  @Test(expected = CompilationFailedException.class)
  public void zeroApiLevel() throws CompilationFailedException {
    DiagnosticsChecker.checkErrorsContains(
        "Invalid argument to --min-api", handler -> parse(handler, "--min-api", "0"));
  }

  @Test
  public void disableDesugaringCli() throws CompilationFailedException {
    BaseCompilerCommandTest.assertDesugaringDisabled(parse("--no-desugaring"));
  }

  @Test
  public void disableDesugaringApi() throws CompilationFailedException {
    BaseCompilerCommandTest.assertDesugaringDisabled(R8Command.builder()
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
        .setDisableDesugaring(true)
        .build());
  }

  private ProcessResult runR8OnShaking1(Path additionalProguardConfiguration) throws Throwable {
    Path input = Paths.get(EXAMPLES_BUILD_DIR, "shaking1.jar").toAbsolutePath();
    Path proguardConfiguration =
        Paths.get(ToolHelper.EXAMPLES_DIR, "shaking1", "keep-rules.txt").toAbsolutePath();
    return ToolHelper.forkR8(temp.getRoot().toPath(),
        "--pg-conf", proguardConfiguration.toString(),
        "--pg-conf", additionalProguardConfiguration.toString(),
        "--lib", ToolHelper.getDefaultAndroidJar().toAbsolutePath().toString(),
        input.toString());
  }

  @Test
  public void printsConfigurationOnStdout() throws Throwable {
    Path proguardPrintConfigurationConfiguration =
        temp.newFile("printconfiguration.txt").toPath().toAbsolutePath();
    FileUtils.writeTextFile(
        proguardPrintConfigurationConfiguration, ImmutableList.of("-printconfiguration"));
    ProcessResult result = runR8OnShaking1(proguardPrintConfigurationConfiguration);
    assertEquals("R8 run failed: " + result.stderr, 0, result.exitCode);
    assertTrue(result.stdout.contains("-printconfiguration"));
  }

  @Test
  public void printsPrintSeedsOnStdout() throws Throwable {
    Path proguardPrintSeedsConfiguration = temp.newFile("printseeds.txt").toPath().toAbsolutePath();
    FileUtils.writeTextFile(proguardPrintSeedsConfiguration, ImmutableList.of("-printseeds"));
    ProcessResult result = runR8OnShaking1(proguardPrintSeedsConfiguration);
    assertEquals("R8 run failed: " + result.stderr, 0, result.exitCode);
    assertTrue(result.stdout.contains("void main(java.lang.String[])"));
  }

  @Test
  public void printsPrintUsageOnStdout() throws Throwable {
    Path proguardPrintUsageConfiguration = temp.newFile("printusage.txt").toPath().toAbsolutePath();
    FileUtils.writeTextFile(proguardPrintUsageConfiguration, ImmutableList.of("-printusage"));
    ProcessResult result = runR8OnShaking1(proguardPrintUsageConfiguration);
    assertEquals("R8 run failed: " + result.stderr, 0, result.exitCode);
    assertTrue(result.stdout.contains("shaking1.Unused"));
  }

  @Test
  public void printsPrintSeedsAndPrintUsageOnStdout() throws Throwable {
    Path proguardPrintSeedsConfiguration =
        temp.newFile("printseedsandprintusage.txt").toPath().toAbsolutePath();
    FileUtils.writeTextFile(
        proguardPrintSeedsConfiguration, ImmutableList.of("-printseeds", "-printusage"));
    ProcessResult result = runR8OnShaking1(proguardPrintSeedsConfiguration);
    assertEquals("R8 run failed: " + result.stderr, 0, result.exitCode);
    assertTrue(result.stdout.contains("void main(java.lang.String[])"));
    assertTrue(result.stdout.contains("shaking1.Unused"));
  }

  @Test
  public void printsPrintSeedsAndPrintUsageAndPrintConfigurationOnStdout() throws Throwable {
    Path proguardPrintSeedsConfiguration =
        temp.newFile("printseedsandprintusageandprintconfiguration.txt").toPath().toAbsolutePath();
    FileUtils.writeTextFile(proguardPrintSeedsConfiguration,
        ImmutableList.of("-printseeds", "-printusage", "-printconfiguration"));
    ProcessResult result = runR8OnShaking1(proguardPrintSeedsConfiguration);
    assertEquals("R8 run failed: " + result.stderr, 0, result.exitCode);
    assertTrue(result.stdout.contains("void main(java.lang.String[])"));
    assertTrue(result.stdout.contains("shaking1.Unused"));
    assertTrue(result.stdout.contains("-printseeds"));
    assertTrue(result.stdout.contains("-printusage"));
    assertTrue(result.stdout.contains("-printconfiguration"));
  }

  @Test
  public void noInputOutputsEmptyZip() throws CompilationFailedException, IOException {
    Path emptyZip = temp.getRoot().toPath().resolve("empty.zip");
    R8.run(
        R8Command.builder()
            .setOutput(emptyZip, OutputMode.DexIndexed)
            .build());
    assertTrue(Files.exists(emptyZip));
    assertEquals(0, new ZipFile(emptyZip.toFile(), StandardCharsets.UTF_8).size());
  }

  private Path writeZipWithDataResource(String name) throws Exception {
    Path dataResourceZip = temp.newFolder().toPath().resolve(name);
    try (ZipOutputStream out =
        new ZipOutputStream(
            Files.newOutputStream(
                dataResourceZip,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING))) {
      // Write a directory entry and a normal entry.
      ZipUtils.writeToZipStream(out, "org/", new byte[] {}, ZipEntry.STORED);
      ZipUtils.writeToZipStream(
          out, "org/resource.txt", "Hello world!".getBytes(), ZipEntry.STORED);
    }
    return dataResourceZip;
  }

  @Test
  public void defaultResourceProcessing() throws Exception {
    Path dataResourceZip = writeZipWithDataResource("dataResource.zip");
    Path outputZip = temp.getRoot().toPath().resolve("output.zip");
    R8.run(
        R8Command.builder()
            .addProgramFiles(dataResourceZip)
            .setOutput(outputZip, OutputMode.ClassFile)
            .build());
    assertTrue(Files.exists(outputZip));
    assertEquals(1, new ZipFile(outputZip.toFile(), StandardCharsets.UTF_8).size());
  }

  public void runCustomResourceProcessing(
      boolean includeDataResources, boolean keepDirectories, int expectedZipEntries)
      throws Exception {
    Path dataResourceZip = writeZipWithDataResource("dataResource.zip");
    Path outputZip = temp.newFolder().toPath().resolve("output.zip");
    R8.run(
        R8Command.builder()
            .addProgramFiles(dataResourceZip)
            .setOutput(outputZip, OutputMode.ClassFile, includeDataResources)
            .addProguardConfiguration(
                ImmutableList.of(keepDirectories ? "-keepdirectories" : ""), Origin.unknown())
            .build());
    assertTrue(Files.exists(outputZip));
    assertEquals(
        expectedZipEntries, new ZipFile(outputZip.toFile(), StandardCharsets.UTF_8).size());
  }

  private Path simpleProguardConfiguration() throws Exception {
    Path proguardConfiguration = temp.newFile("printseedsandprintusage.txt").toPath();
    FileUtils.writeTextFile(proguardConfiguration, ImmutableList.of("-keep class A { *; }"));
    return proguardConfiguration;
  }

  @Test
  public void noTreeShakingOption() throws Throwable {
    // Default "keep all" rule implies no tree shaking.
    assertTrue(parse().getEnableTreeShaking());
    assertFalse(parse("--no-tree-shaking").getEnableTreeShaking());

    // With a Proguard configuration --no-tree-shaking takes effect.
    String proguardConfiguration = simpleProguardConfiguration().toAbsolutePath().toString();
    assertTrue(parse("--pg-conf", proguardConfiguration).getEnableTreeShaking());
    assertFalse(
        parse("--no-tree-shaking", "--pg-conf", proguardConfiguration).getEnableTreeShaking());
  }

  @Test
  public void noMinificationOption() throws Throwable {
    // Default "keep all" rule implies no tree minification.
    assertTrue(parse().getEnableMinification());
    assertFalse(parse("--no-minification").getEnableMinification());

    // With a Proguard configuration --no-tree-shaking takes effect.
    String proguardConfiguration = simpleProguardConfiguration().toAbsolutePath().toString();
    assertTrue(parse("--pg-conf", proguardConfiguration).getEnableMinification());
    assertFalse(
        parse("--no-minification", "--pg-conf", proguardConfiguration).getEnableMinification());
  }

  @Test
  public void setPgConfOutputFlag() throws Throwable {
    Path file = temp.newFolder().toPath().resolve("output.conf");
    R8Command command = parse("--pg-conf-output", file.toString());
    InternalOptions options = command.getInternalOptions();
    assertNotNull(options.configurationConsumer);
  }

  @Test
  public void defaultDataResourcesOption() throws Throwable {
    Path dataResourceZip = writeZipWithDataResource("dataResource.zip");
    Path outputZip = temp.newFolder().toPath().resolve("output.zip");

    R8.run(
        parse(
            dataResourceZip.toAbsolutePath().toString(),
            "--output",
            outputZip.toAbsolutePath().toString()));
    assertTrue(Files.exists(outputZip));
    assertEquals(1, new ZipFile(outputZip.toFile(), StandardCharsets.UTF_8).size());
  }

  @Test
  public void noDataResourcesOption() throws Throwable {
    Path dataResourceZip = writeZipWithDataResource("dataResource.zip");
    Path outputZip = temp.newFolder().toPath().resolve("output.zip");

    R8.run(
        parse(
            "--no-data-resources",
            dataResourceZip.toAbsolutePath().toString(),
            "--output",
            outputZip.toAbsolutePath().toString()));
    assertTrue(Files.exists(outputZip));
    assertEquals(0, new ZipFile(outputZip.toFile(), StandardCharsets.UTF_8).size());
  }

  @Test
  public void customResourceProcessing() throws Exception {
    runCustomResourceProcessing(true, true, 2);
    runCustomResourceProcessing(true, false, 1);
    runCustomResourceProcessing(false, false, 0);
  }

  private void checkSingleForceAllAssertion(
      List<AssertionsConfiguration> entries, Predicate<AssertionsConfiguration> x) {
    assertEquals(1, entries.size());
    assertTrue(x.test(entries.get(0)));
    assertEquals(AssertionTransformationScope.ALL, entries.get(0).getScope());
  }

  private void checkSingleForceClassAndPackageAssertion(
      List<AssertionsConfiguration> entries, Predicate<AssertionsConfiguration> x) {
    assertEquals(2, entries.size());
    assertTrue(x.test(entries.get(0)));
    assertEquals(AssertionTransformationScope.CLASS, entries.get(0).getScope());
    assertEquals("ClassName", entries.get(0).getValue());
    assertTrue(x.test(entries.get(1)));
    assertEquals(AssertionTransformationScope.PACKAGE, entries.get(1).getScope());
    assertEquals("PackageName", entries.get(1).getValue());
  }

  private void checkSingleForceClassAndPackageAssertion(
      List<AssertionsConfiguration> entries,
      Predicate<AssertionsConfiguration> checkClass,
      Predicate<AssertionsConfiguration> checkPackage) {
    assertEquals(2, entries.size());
    assertTrue(checkClass.test(entries.get(0)));
    assertEquals(AssertionTransformationScope.CLASS, entries.get(0).getScope());
    assertEquals("ClassName", entries.get(0).getValue());
    assertTrue(checkPackage.test(entries.get(1)));
    assertEquals(AssertionTransformationScope.PACKAGE, entries.get(1).getScope());
    assertEquals("PackageName", entries.get(1).getValue());
  }

  @Test
  public void forceAssertionOption() throws Exception {
    checkSingleForceAllAssertion(
        parse("--force-enable-assertions").getAssertionsConfiguration(),
        AssertionsConfiguration::isCompileTimeEnabled);
    checkSingleForceAllAssertion(
        parse("--force-disable-assertions").getAssertionsConfiguration(),
        AssertionsConfiguration::isCompileTimeDisabled);
    checkSingleForceAllAssertion(
        parse("--force-passthrough-assertions").getAssertionsConfiguration(),
        AssertionsConfiguration::isPassthrough);
    checkSingleForceClassAndPackageAssertion(
        parse("--force-enable-assertions:ClassName", "--force-enable-assertions:PackageName...")
            .getAssertionsConfiguration(),
        AssertionsConfiguration::isCompileTimeEnabled);
    checkSingleForceClassAndPackageAssertion(
        parse("--force-disable-assertions:ClassName", "--force-disable-assertions:PackageName...")
            .getAssertionsConfiguration(),
        AssertionsConfiguration::isCompileTimeDisabled);
    checkSingleForceClassAndPackageAssertion(
        parse(
                "--force-passthrough-assertions:ClassName",
                "--force-passthrough-assertions:PackageName...")
            .getAssertionsConfiguration(),
        AssertionsConfiguration::isPassthrough);
    checkSingleForceAllAssertion(
        parse("--force-assertions-handler:com.example.MyHandler.handler")
            .getAssertionsConfiguration(),
        configuration ->
            configuration.isAssertionHandler()
                && configuration
                    .getAssertionHandler()
                    .getHolderClass()
                    .equals(Reference.classFromDescriptor("Lcom/example/MyHandler;"))
                && configuration.getAssertionHandler().getMethodName().equals("handler")
                && configuration
                    .getAssertionHandler()
                    .getMethodDescriptor()
                    .equals("(Ljava/lang/Throwable;)V"));
    checkSingleForceClassAndPackageAssertion(
        parse(
                "--force-assertions-handler:com.example.MyHandler.handler1:ClassName",
                "--force-assertions-handler:com.example.MyHandler.handler2:PackageName...")
            .getAssertionsConfiguration(),
        configuration ->
            configuration.isAssertionHandler()
                && configuration
                    .getAssertionHandler()
                    .getHolderClass()
                    .equals(Reference.classFromDescriptor("Lcom/example/MyHandler;"))
                && configuration.getAssertionHandler().getMethodName().equals("handler1")
                && configuration
                    .getAssertionHandler()
                    .getMethodDescriptor()
                    .equals("(Ljava/lang/Throwable;)V"),
        configuration ->
            configuration.isAssertionHandler()
                && configuration
                    .getAssertionHandler()
                    .getHolderClass()
                    .equals(Reference.classFromDescriptor("Lcom/example/MyHandler;"))
                && configuration.getAssertionHandler().getMethodName().equals("handler2")
                && configuration
                    .getAssertionHandler()
                    .getMethodDescriptor()
                    .equals("(Ljava/lang/Throwable;)V"));
  }

  @Test(expected = CompilationFailedException.class)
  public void missingParameterForLastOption() throws CompilationFailedException {
    DiagnosticsChecker.checkErrorsContains(
        "Missing parameter", handler -> parse(handler, "--output"));
  }

  @Test
  public void desugaredLibrary() throws CompilationFailedException, IOException {
    R8Command r8Command =
        parse(
            "--desugared-lib",
            LibraryDesugaringSpecification.JDK11.getSpecification().toString(),
            "--lib",
            ToolHelper.getAndroidJar(AndroidApiLevel.R).toString());
    InternalOptions options = getOptionsWithLoadedDesugaredLibraryConfiguration(r8Command, false);
    assertFalse(options.machineDesugaredLibrarySpecification.getRewriteType().isEmpty());
  }

  @Test
  public void desugaredLibraryWithOutputConf() throws CompilationFailedException, IOException {
    Path pgout = temp.getRoot().toPath().resolve("pgout.conf");
    R8Command r8Command =
        parse(
            "--desugared-lib",
            LibraryDesugaringSpecification.JDK11.getSpecification().toString(),
            "--lib",
            ToolHelper.getAndroidJar(AndroidApiLevel.R).toString(),
            "--desugared-lib-pg-conf-output",
            pgout.toString());
    InternalOptions options = getOptionsWithLoadedDesugaredLibraryConfiguration(r8Command, false);
    assertFalse(options.machineDesugaredLibrarySpecification.getRewriteType().isEmpty());
  }

  @Test
  public void desugaredLibraryWithOutputConfMissingArg() {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    try {
      parse(
          diagnostics,
          "--desugared-lib",
          LibraryDesugaringSpecification.JDK11.getSpecification().toString(),
          "--desugared-lib-pg-conf-output");
      fail("Expected parse error");
    } catch (CompilationFailedException e) {
      diagnostics.assertErrorsMatch(diagnosticMessage(containsString("Missing parameter")));
    }
  }

  @Test
  public void numThreadsOption() throws Exception {
    assertEquals(ThreadUtils.NOT_SPECIFIED, parse().getThreadCount());
    assertEquals(1, parse("--thread-count", "1").getThreadCount());
    assertEquals(2, parse("--thread-count", "2").getThreadCount());
    assertEquals(10, parse("--thread-count", "10").getThreadCount());
  }

  private void numThreadsOptionInvalid(String value) {
    final String expectedErrorContains = "Invalid argument to --thread-count";
    try {
      DiagnosticsChecker.checkErrorsContains(
          expectedErrorContains, handler -> parse(handler, "--thread-count", value));
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void numThreadsOptionInvalid() throws Exception {
    numThreadsOptionInvalid("0");
    numThreadsOptionInvalid("-1");
    numThreadsOptionInvalid("two");
  }

  @Test
  public void defaultApiModelingState() throws Exception {
    ApiModelTestingOptions options = parse("").getInternalOptions().apiModelingOptions();
    assertTrue(options.isApiCallerIdentificationEnabled());
    assertTrue(options.enableOutliningOfMethods);
    assertTrue(options.enableStubbingOfClasses);
  }

  @Test
  public void androidPlatformBuildFlag() throws Exception {
    assertFalse(parse().getAndroidPlatformBuild());
    assertTrue(parse("--android-platform-build").getAndroidPlatformBuild());
  }

  @Override
  String[] requiredArgsForTest() {
    return new String[0];
  }

  @Override
  R8Command parse(String... args) throws CompilationFailedException {
    return R8Command.parse(args, EmbeddedOrigin.INSTANCE).build();
  }

  @Override
  R8Command parse(DiagnosticsHandler handler, String... args) throws CompilationFailedException {
    return R8Command.parse(args, EmbeddedOrigin.INSTANCE, handler).build();
  }
}
