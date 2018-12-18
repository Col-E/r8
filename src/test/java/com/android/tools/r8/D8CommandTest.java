// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.R8CommandTest.getOutputPath;
import static com.android.tools.r8.ToolHelper.EXAMPLES_BUILD_DIR;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.sdklib.AndroidVersion;
import com.android.tools.r8.D8CommandParser.OrderedClassFileResourceProvider;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.origin.EmbeddedOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class D8CommandTest {

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test(expected = CompilationFailedException.class)
  public void emptyBuilder() throws Throwable {
    verifyEmptyCommand(D8Command.builder().build());
  }

  @Test
  public void emptyCommand() throws Throwable {
    verifyEmptyCommand(
        D8Command.builder().setProgramConsumer(DexIndexedConsumer.emptyConsumer()).build());
    verifyEmptyCommand(parse());
    verifyEmptyCommand(parse(""));
    verifyEmptyCommand(parse("", ""));
    verifyEmptyCommand(parse(" "));
    verifyEmptyCommand(parse(" ", " "));
    verifyEmptyCommand(parse("\t"));
    verifyEmptyCommand(parse("\t", "\t"));
  }

  private void verifyEmptyCommand(D8Command command) throws Throwable {
    assertEquals(CompilationMode.DEBUG, command.getMode());
    assertEquals(AndroidVersion.DEFAULT.getApiLevel(), command.getMinApiLevel());
    assertTrue(command.getProgramConsumer() instanceof DexIndexedConsumer);
    AndroidApp app = ToolHelper.getApp(command);
    assertEquals(0, app.getDexProgramResourcesForTesting().size());
    assertEquals(0, app.getClassProgramResourcesForTesting().size());
  }

  @Test
  public void allowDexFilePerClassFileBuilder() throws Throwable {
    assertTrue(
        D8Command.builder()
                .setProgramConsumer(DexFilePerClassFileConsumer.emptyConsumer())
                .build()
                .getProgramConsumer()
            instanceof DexFilePerClassFileConsumer);
  }

  @Test(expected = CompilationFailedException.class)
  public void disallowClassFileConsumer() throws Throwable {
    D8Command.builder().setProgramConsumer(ClassFileConsumer.emptyConsumer()).build();
  }

  @Test
  public void defaultOutIsCwd() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path input = Paths.get(EXAMPLES_BUILD_DIR + "/arithmetic.jar").toAbsolutePath();
    Path output = working.resolve("classes.dex");
    assertFalse(Files.exists(output));
    assertEquals(0, ToolHelper.forkD8(working, input.toString()).exitCode);
    assertTrue(Files.exists(output));
  }

  @Test
  public void flagsFile() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path flagsFile = working.resolve("flags.txt");
    Path input = Paths.get(EXAMPLES_BUILD_DIR + "/arithmetic.jar").toAbsolutePath();
    Path output = working.resolve("output.zip");
    FileUtils.writeTextFile(
        flagsFile,
        "--output",
        "output.zip",
        "--min-api",
        "24",
        input.toString());
    assertEquals(0, ToolHelper.forkD8(working, "@flags.txt").exitCode);
    assertTrue(Files.exists(output));
    Collection<Marker> markers = ExtractMarker.extractMarkerFromDexFile(output);
    assertEquals(1, markers.size());
    Marker marker = markers.iterator().next();
    assertEquals(24, marker.getMinApi().intValue());
    assertEquals(Tool.D8, marker.getTool());
  }

  @Test(expected=CompilationFailedException.class)
  public void nonExistingFlagsFile() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path flags = working.resolve("flags.txt").toAbsolutePath();
    assertNotEquals(0, ToolHelper.forkR8(working, "@flags.txt").exitCode);
    DiagnosticsChecker.checkErrorsContains("File not found", handler ->
        D8.run(
            D8Command.parse(
                new String[] { "@" + flags.toString() },
                EmbeddedOrigin.INSTANCE,
                handler).build()));
  }

  @Test
  public void printsHelpOnNoInput() throws Throwable {
    ProcessResult result = ToolHelper.forkD8(temp.getRoot().toPath());
    assertFalse(result.exitCode == 0);
    assertTrue(result.stderr.contains("Usage"));
    assertFalse(result.stderr.contains("D8_foobar")); // Sanity check
  }

  @Test
  public void validOutputPath() throws Throwable {
    Path existingDir = temp.getRoot().toPath();
    Path nonExistingZip = existingDir.resolve("a-non-existing-archive.zip");
    assertEquals(
        existingDir,
        getOutputPath(D8Command.builder().setOutput(existingDir, OutputMode.DexIndexed).build()));
    assertEquals(
        nonExistingZip,
        getOutputPath(
            D8Command.builder().setOutput(nonExistingZip, OutputMode.DexIndexed).build()));
    assertEquals(existingDir, getOutputPath(parse("--output", existingDir.toString())));
    assertEquals(nonExistingZip, getOutputPath(parse("--output", nonExistingZip.toString())));
  }

  @Test(expected = CompilationFailedException.class)
  public void nonExistingOutputDir() throws Throwable {
    Path nonExistingDir = temp.getRoot().toPath().resolve("a/path/that/does/not/exist");
    D8Command.builder().setOutput(nonExistingDir, OutputMode.DexIndexed).build();
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
    Path input = Paths.get(EXAMPLES_BUILD_DIR, "arithmetic.jar");
    ProcessResult result =
        ToolHelper.forkD8(Paths.get("."), input.toString(), "--output", existingDir.toString());
    assertEquals(result.toString(), 0, result.exitCode);
    assertTrue(Files.exists(classesFiles.get(0)));
    for (int i = 1; i < classesFiles.size(); i++) {
      Path file = classesFiles.get(i);
      assertFalse("Expected stale file to be gone: " + file, Files.exists(file));
    }
    for (Path file : otherFiles) {
      assertTrue("Expected non-classes file to remain: " + file, Files.exists(file));
    }
  }

  @Test
  public void existingOutputZip() throws Throwable {
    Path existingZip = temp.newFile("an-existing-archive.zip").toPath();
    D8Command.builder().setOutput(existingZip, OutputMode.DexIndexed).build();
  }

  @Test(expected = CompilationFailedException.class)
  public void invalidOutputFileType() throws Throwable {
    Path invalidType = temp.getRoot().toPath().resolve("an-invalid-output-file-type.foobar");
    D8Command.builder().setOutput(invalidType, OutputMode.DexIndexed).build();
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

  @Test
  public void mainDexList() throws Throwable {
    Path mainDexList1 = temp.newFile("main-dex-list-1.txt").toPath();
    Path mainDexList2 = temp.newFile("main-dex-list-2.txt").toPath();

    D8Command command = parse("--main-dex-list", mainDexList1.toString());
    assertTrue(ToolHelper.getApp(command).hasMainDexListResources());

    command = parse(
        "--main-dex-list", mainDexList1.toString(), "--main-dex-list", mainDexList2.toString());
    assertTrue(ToolHelper.getApp(command).hasMainDexListResources());
  }

  @Test(expected = CompilationFailedException.class)
  public void nonExistingMainDexList() throws Throwable {
    Path mainDexList = temp.getRoot().toPath().resolve("main-dex-list.txt");
    parse("--main-dex-list", mainDexList.toString());
  }

  @Test(expected = CompilationFailedException.class)
  public void mainDexListWithFilePerClass() throws Throwable {
    Path mainDexList = temp.newFile("main-dex-list.txt").toPath();
    D8Command command = parse("--main-dex-list", mainDexList.toString(), "--file-per-class");
    assertTrue(ToolHelper.getApp(command).hasMainDexListResources());
  }

  @Test(expected = CompilationFailedException.class)
  public void mainDexListWithIntermediate() throws Throwable {
    Path mainDexList = temp.newFile("main-dex-list.txt").toPath();
    D8Command command = parse("--main-dex-list", mainDexList.toString(), "--intermediate");
    assertTrue(ToolHelper.getApp(command).hasMainDexListResources());
  }

  @Test(expected = CompilationFailedException.class)
  public void mainDexListWithNonLegacyMinApi() throws Throwable {
    Path mainDexList = temp.newFile("main-dex-list.txt").toPath();
    DiagnosticsChecker.checkErrorsContains(
        "does not support main-dex",
        (handler) ->
            D8Command.builder(handler)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .setMinApiLevel(AndroidApiLevel.L.getLevel())
                .addMainDexListFiles(mainDexList)
                .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void invalidOutputFileTypeParse() throws Throwable {
    Path invalidType = temp.getRoot().toPath().resolve("an-invalid-output-file-type.foobar");
    parse("--output", invalidType.toString());
  }

  @Test
  public void folderLibAndClasspath() throws Throwable {
    Path inputFile =
        Paths.get(ToolHelper.EXAMPLES_ANDROID_N_BUILD_DIR, "interfacemethods" + JAR_EXTENSION);
    Path tmpClassesDir = temp.newFolder().toPath();
    ZipUtils.unzip(inputFile.toString(), tmpClassesDir.toFile());
    D8Command command = parse("--lib", tmpClassesDir.toString(), "--classpath",
        tmpClassesDir.toString());
    AndroidApp inputApp = ToolHelper.getApp(command);
    assertEquals(1, inputApp.getClasspathResourceProviders().size());
    OrderedClassFileResourceProvider classpathProvider =
        (OrderedClassFileResourceProvider) inputApp.getClasspathResourceProviders().get(0);
    assertEquals(1, classpathProvider.providers.size());
    assertTrue(Files.isSameFile(tmpClassesDir,
        ((DirectoryClassFileProvider) classpathProvider.providers.get(0)).getRoot()));
    assertEquals(1, inputApp.getLibraryResourceProviders().size());
    assertTrue(Files.isSameFile(tmpClassesDir,
        ((DirectoryClassFileProvider) inputApp.getLibraryResourceProviders().get(0)).getRoot()));
  }

  @Test
  public void folderClasspathMultiple() throws Throwable {
    Path inputFile =
        Paths.get(ToolHelper.EXAMPLES_ANDROID_N_BUILD_DIR, "interfacemethods" + JAR_EXTENSION);
    Path tmpClassesDir1 = temp.newFolder().toPath();
    Path tmpClassesDir2 = temp.newFolder().toPath();
    ZipUtils.unzip(inputFile.toString(), tmpClassesDir1.toFile());
    ZipUtils.unzip(inputFile.toString(), tmpClassesDir2.toFile());
    D8Command command = parse("--classpath", tmpClassesDir1.toString(), "--classpath",
        tmpClassesDir2.toString());
    AndroidApp inputApp = ToolHelper.getApp(command);
    assertEquals(1, inputApp.getClasspathResourceProviders().size());
    OrderedClassFileResourceProvider classpathProvider =
        (OrderedClassFileResourceProvider) inputApp.getClasspathResourceProviders().get(0);
    assertEquals(2, classpathProvider.providers.size());
    assertTrue(Files.isSameFile(tmpClassesDir1,
        ((DirectoryClassFileProvider) classpathProvider.providers.get(0)).getRoot()));
    assertTrue(Files.isSameFile(tmpClassesDir2,
        ((DirectoryClassFileProvider) classpathProvider.providers.get(1)).getRoot()));
  }

  @Test(expected = CompilationFailedException.class)
  public void classFolderProgram() throws Throwable {
    Path inputFile =
        Paths.get(ToolHelper.EXAMPLES_ANDROID_N_BUILD_DIR, "interfacemethods" + JAR_EXTENSION);
    Path tmpClassesDir = temp.newFolder().toPath();
    ZipUtils.unzip(inputFile.toString(), tmpClassesDir.toFile());
    parse(tmpClassesDir.toString());
  }

  @Test(expected = CompilationFailedException.class)
  public void emptyFolderProgram() throws Throwable {
    Path tmpClassesDir = temp.newFolder().toPath();
    parse(tmpClassesDir.toString());
  }

  @Test
  public void nonExistingOutputJar() throws Throwable {
    Path nonExistingJar = temp.getRoot().toPath().resolve("non-existing-archive.jar");
    D8Command.builder().setOutput(nonExistingJar, OutputMode.DexIndexed).build();
  }

  @Test(expected = CompilationFailedException.class)
  public void vdexFileUnsupported() throws Throwable {
    Path vdexFile = temp.newFile("test.vdex").toPath();
    D8Command.builder()
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
        .addProgramFiles(vdexFile)
        .build();
  }

  @Test
  public void addProgramResources() throws ResourceException, CompilationFailedException {

    // Stub out a custom origin to identify our resources.
    class MyOrigin extends Origin {

      public MyOrigin() {
        super(Origin.root());
      }

      @Override
      public String part() {
        return "MyOrigin";
      }
    }

    Path input = Paths.get(EXAMPLES_BUILD_DIR, "arithmetic.jar");
    ProgramResourceProvider myProvider =
        ArchiveProgramResourceProvider.fromSupplier(
            new MyOrigin(), () -> new ZipFile(input.toFile(), StandardCharsets.UTF_8));
    D8Command command =
        D8Command.builder()
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .addProgramResourceProvider(myProvider)
            .build();

    // Check that each resource was provided by our provider.
    ProgramResourceProvider inAppProvider =
        command.getInputApp().getProgramResourceProviders().get(0);
    for (ProgramResource resource : inAppProvider.getProgramResources()) {
      Origin outermost = resource.getOrigin();
      while (outermost.parent() != null && outermost.parent() != Origin.root()) {
        outermost = outermost.parent();
      }
      assertTrue(outermost instanceof MyOrigin);
    }
  }

  @Test(expected = CompilationFailedException.class)
  public void addMultiTypeProgramConsumer() throws CompilationFailedException {
    class MultiTypeConsumer implements DexIndexedConsumer, DexFilePerClassFileConsumer {

      @Override
      public void accept(
          String primaryClassDescriptor,
          ByteDataView data,
          Set<String> descriptors,
          DiagnosticsHandler handler) {}

      @Override
      public void accept(
          int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {}

      @Override
      public void finished(DiagnosticsHandler handler) {

      }
    }

    D8Command.builder().setProgramConsumer(new MultiTypeConsumer()).build();
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
    BaseCompilerCommandTest.assertDesugaringDisabled(D8Command.builder()
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
        .setDisableDesugaring(true)
        .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void errorOnEmptyClassfile() throws IOException, CompilationFailedException {
    Path emptyFile = temp.getRoot().toPath().resolve("empty-file.class");
    FileUtils.writeToFile(emptyFile, null, new byte[0]);
    DiagnosticsChecker.checkErrorsContains(
        "empty",
        handler ->
            D8.run(
                D8Command.builder(handler)
                    .addProgramFiles(emptyFile)
                    .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                    .build()));
  }

  @Test(expected = CompilationFailedException.class)
  public void errorOnInvalidClassfileHeader() throws IOException, CompilationFailedException {
    Path emptyFile = temp.getRoot().toPath().resolve("empty-file.class");
    FileUtils.writeToFile(emptyFile, null, new byte[] {'C', 'A', 'F', 'E', 'B', 'A', 'B', 'F'});
    DiagnosticsChecker.checkErrorsContains(
        "header",
        handler ->
            D8.run(
                D8Command.builder(handler)
                    .addProgramFiles(emptyFile)
                    .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                    .build()));
  }

  @Test(expected = CompilationFailedException.class)
  public void errorOnEmptyDex() throws IOException, CompilationFailedException {
    Path emptyFile = temp.getRoot().toPath().resolve("empty-file.dex");
    FileUtils.writeToFile(emptyFile, null, new byte[0]);
    DiagnosticsChecker.checkErrorsContains(
        "empty",
        handler ->
            D8.run(
                D8Command.builder(handler)
                    .addProgramFiles(emptyFile)
                    .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                    .build()));
  }

  @Test(expected = CompilationFailedException.class)
  public void errorOnInvalidDexHeader() throws IOException, CompilationFailedException {
    Path emptyFile = temp.getRoot().toPath().resolve("empty-file.dex");
    FileUtils.writeToFile(emptyFile, null, new byte[] {'C', 'A', 'F', 'E', 'B', 'A', 'B', 'E'});
    DiagnosticsChecker.checkErrorsContains(
        "header",
        handler ->
            D8.run(
                D8Command.builder(handler)
                    .addProgramFiles(emptyFile)
                    .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                    .build()));
  }

  @Test
  public void noInputOutputsEmptyZip() throws CompilationFailedException, IOException {
    Path emptyZip = temp.getRoot().toPath().resolve("empty.zip");
    D8.run(
        D8Command.builder()
            .setOutput(emptyZip, OutputMode.DexIndexed)
            .build());
    assertTrue(Files.exists(emptyZip));
    assertEquals(0, new ZipFile(emptyZip.toFile(), StandardCharsets.UTF_8).size());
  }

  private D8Command parse(String... args) throws CompilationFailedException {
    return D8Command.parse(args, EmbeddedOrigin.INSTANCE).build();
  }

  private D8Command parse(DiagnosticsHandler handler, String... args)
      throws CompilationFailedException {
    return D8Command.parse(args, EmbeddedOrigin.INSTANCE, handler).build();
  }
}
