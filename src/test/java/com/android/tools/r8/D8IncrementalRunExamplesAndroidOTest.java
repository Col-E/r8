// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringForTesting.getCompanionClassNameSuffix;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8Command.Builder;
import com.android.tools.r8.Disassemble.DisassembleCommand;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.OffOrAuto;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public abstract class D8IncrementalRunExamplesAndroidOTest
    extends RunExamplesAndroidOTest<D8Command.Builder> {

  abstract class D8IncrementalTestRunner extends TestRunner<D8IncrementalTestRunner> {

    D8IncrementalTestRunner(String testName, String packageName, String mainClass) {
      super(testName, packageName, mainClass);
    }

    @Override
    D8IncrementalTestRunner withMinApiLevel(AndroidApiLevel minApiLevel) {
      return withBuilderTransformation(builder -> builder.setMinApiLevel(minApiLevel.getLevel()));
    }

    @Override
    void build(Path testJarFile, Path out, OutputMode mode) throws Throwable {
      Map<String, ProgramResource> files = compileClassesTogether(testJarFile, null);
      mergeClassFiles(Lists.newArrayList(files.values()), out, mode);
    }

    // Dex classes separately.
    SortedMap<String, ProgramResource> compileClassesSeparately(Path testJarFile) throws Throwable {
      TreeMap<String, ProgramResource> fileToResource = new TreeMap<>();
      List<String> classFiles = collectClassFiles(testJarFile);
      for (String classFile : classFiles) {
        AndroidApp app =
            compileClassFilesInIntermediate(
                testJarFile, Collections.singletonList(classFile), null, OutputMode.DexIndexed);
        assert app.getDexProgramResourcesForTesting().size() == 1;
        fileToResource.put(
            makeRelative(testJarFile, Paths.get(classFile)).toString(),
            app.getDexProgramResourcesForTesting().get(0));
      }
      return fileToResource;
    }

    // Dex classes in one D8 invocation.
    SortedMap<String, ProgramResource> compileClassesTogether(Path testJarFile, Path output)
        throws Throwable {
      TreeMap<String, ProgramResource> fileToResource = new TreeMap<>();
      List<String> classFiles = collectClassFiles(testJarFile);
      AndroidApp app =
          compileClassFilesInIntermediate(
              testJarFile, classFiles, output, OutputMode.DexFilePerClassFile);
      for (ProgramResource resource : app.getDexProgramResourcesForTesting()) {
        Set<String> descriptors = resource.getClassDescriptors();
        String mainClassDescriptor = app.getPrimaryClassDescriptor(resource);
        Assert.assertNotNull(mainClassDescriptor);
        for (String descriptor : descriptors) {
          // classes are either lambda classes used by the main class, companion classes of the main
          // interface, the main class/interface, or for JDK9, desugaring of try-with-resources.
          ClassReference reference = Reference.classFromDescriptor(descriptor);
          Assert.assertTrue(
              descriptor.endsWith(getCompanionClassNameSuffix() + ";")
                  || SyntheticItemsTestUtils.isExternalTwrCloseMethod(reference)
                  || SyntheticItemsTestUtils.isMaybeExternalSuppressedExceptionMethod(reference)
                  || SyntheticItemsTestUtils.isExternalLambda(reference)
                  || SyntheticItemsTestUtils.isExternalStaticInterfaceCall(reference)
                  || descriptor.equals(mainClassDescriptor));
        }
        String classDescriptor =
            DescriptorUtils.getClassBinaryNameFromDescriptor(mainClassDescriptor);
        String classFilePath = classDescriptor + ".class";
        if (File.separatorChar != '/') {
          classFilePath = classFilePath.replace('/', File.separatorChar);
        }
        fileToResource.put(classFilePath, resource);
      }
      return fileToResource;
    }

    private Path makeRelative(Path testJarFile, Path classFile) {
      Path regularParent =
          testJarFile.getParent().resolve(Paths.get("classes"));
      Path legacyParent = regularParent.resolve(Paths.get("..",
          regularParent.getFileName().toString() + "Legacy", "classes"));

      if (classFile.startsWith(regularParent)) {
        return regularParent.relativize(classFile);
      }
      Assert.assertTrue(classFile.startsWith(legacyParent));
      return legacyParent.relativize(classFile);
    }

    private List<String> collectClassFiles(Path testJarFile) {
      List<String> result = new ArrayList<>();
      // Collect Java 8 classes.
      collectClassFiles(getClassesRoot(testJarFile), result);
      // Collect legacy classes.
      collectClassFiles(getLegacyClassesRoot(testJarFile), result);
      Collections.sort(result);
      return result;
    }

    Path getClassesRoot(Path testJarFile) {
      Path parent = testJarFile.getParent();
      return parent.resolve(Paths.get("classes", packageName));
    }

    Path getLegacyClassesRoot(Path testJarFile) {
      Path parent = testJarFile.getParent();
      Path legacyPath = Paths.get("..",
          parent.getFileName().toString() + "Legacy", "classes", packageName);
      return parent.resolve(legacyPath);
    }

    private void collectClassFiles(Path dir, List<String> result) {
      if (Files.exists(dir)) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
          for (Path entry: stream) {
            if (Files.isDirectory(entry)) {
              collectClassFiles(entry, result);
            } else {
              result.add(entry.toString());
            }
          }
        } catch (IOException x) {
          throw new AssertionError(x);
        }
      }
    }

    AndroidApp compileClassFilesInIntermediate(
        Path testJarFile, List<String> inputFiles, Path outputPath, OutputMode outputMode)
        throws Throwable {
      D8Command.Builder builder = D8Command.builder();
      addClasspathReference(testJarFile, builder);
      for (String inputFile : inputFiles) {
        builder.addProgramFiles(Paths.get(inputFile));
      }
      for (Consumer<D8Command.Builder> transformation : builderTransformations) {
        transformation.accept(builder);
      }
      if (outputPath != null) {
        builder.setOutput(outputPath, outputMode);
      } else if (outputMode == OutputMode.DexIndexed) {
        builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
      } else if (outputMode == OutputMode.DexFilePerClassFile) {
        builder.setProgramConsumer(DexFilePerClassFileConsumer.emptyConsumer());
      } else {
        throw new Unreachable("Unexpected output mode " + outputMode);
      }
      builder.setIntermediate(true);
      addLibraryReference(builder, ToolHelper.getAndroidJar(
          androidJarVersion == null ? builder.getMinApiLevel() : androidJarVersion.getLevel()));
      try {
        return ToolHelper.runD8(builder, this::combinedOptionConsumer);
      } catch (Unimplemented | CompilationError | InternalCompilerError re) {
        throw re;
      } catch (RuntimeException re) {
        throw re.getCause() == null ? re : re.getCause();
      }
    }

    AndroidApp mergeClassFiles(List<ProgramResource> dexFiles, Path out) throws Throwable {
      return mergeClassFiles(dexFiles, out, OutputMode.DexIndexed);
    }

    AndroidApp mergeClassFiles(
        List<ProgramResource> dexFiles, Path outputPath, OutputMode outputMode) throws Throwable {
      Builder builder = D8Command.builder();
      for (ProgramResource dexFile : dexFiles) {
        builder.addDexProgramData(readResource(dexFile), dexFile.getOrigin());
      }
      for (Consumer<D8Command.Builder> transformation : builderTransformations) {
        transformation.accept(builder);
      }
      if (outputPath != null) {
        builder.setOutput(outputPath, outputMode);
      } else if (outputMode == OutputMode.DexIndexed) {
        builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
      } else if (outputMode == OutputMode.DexFilePerClassFile) {
        builder.setProgramConsumer(DexFilePerClassFileConsumer.emptyConsumer());
      } else {
        throw new Unreachable("Unexpected output mode " + outputMode);
      }
      try {
        AndroidApp app = ToolHelper.runD8(builder, this::combinedOptionConsumer);
        assert app.getDexProgramResourcesForTesting().size() == 1;
        return app;
      } catch (Unimplemented | CompilationError | InternalCompilerError re) {
        throw re;
      } catch (RuntimeException re) {
        throw re.getCause() == null ? re : re.getCause();
      }
    }

    abstract void addClasspathReference(Path testJarFile, D8Command.Builder builder);

    abstract void addLibraryReference(Builder builder, Path location) throws IOException;
  }

  @Test
  public void dexPerClassFileNoDesugaring() throws Throwable {
    String testName = "dexPerClassFileNoDesugaring";
    String testPackage = "incremental";
    String mainClass = "IncrementallyCompiled";

    Path inputJarFile = Paths.get(EXAMPLE_DIR, testPackage + JAR_EXTENSION);

    D8IncrementalTestRunner test = test(testName, testPackage, mainClass);

    Map<String, ProgramResource> compiledSeparately = test.compileClassesSeparately(inputJarFile);
    Map<String, ProgramResource> compiledTogether = test.compileClassesTogether(inputJarFile, null);
    Assert.assertEquals(compiledSeparately.size(), compiledTogether.size());

    for (Map.Entry<String, ProgramResource> entry : compiledSeparately.entrySet()) {
      ProgramResource otherResource = compiledTogether.get(entry.getKey());
      Assert.assertNotNull(otherResource);
      Assert.assertArrayEquals(readResource(entry.getValue()), readResource(otherResource));
    }

    AndroidApp mergedFromCompiledSeparately =
        test.mergeClassFiles(Lists.newArrayList(compiledSeparately.values()), null);
    AndroidApp mergedFromCompiledTogether =
        test.mergeClassFiles(Lists.newArrayList(compiledTogether.values()), null);

    // TODO(b/123504206): Add a main method and test the output runs.

    Assert.assertArrayEquals(
        readResource(mergedFromCompiledSeparately.getDexProgramResourcesForTesting().get(0)),
        readResource(mergedFromCompiledTogether.getDexProgramResourcesForTesting().get(0)));
  }

  @Test
  public void dexPerClassFileWithDesugaring() throws Throwable {
    String testName = "dexPerClassFileWithDesugaring";
    String testPackage = "lambdadesugaringnplus";
    String mainClass = "LambdasWithStaticAndDefaultMethods";

    Path inputJarFile = Paths.get(EXAMPLE_DIR, testPackage + JAR_EXTENSION);

    D8IncrementalTestRunner test = test(testName, testPackage, mainClass);
    test.withInterfaceMethodDesugaring(OffOrAuto.Auto);

    AndroidApp mergedFromCompiledSeparately =
        test.mergeClassFiles(
            Lists.newArrayList(test.compileClassesSeparately(inputJarFile).values()), null);
    AndroidApp mergedFromCompiledTogether =
        test.mergeClassFiles(
            Lists.newArrayList(test.compileClassesTogether(inputJarFile, null).values()), null);

    Path out1 = temp.newFolder().toPath().resolve("out-together.zip");
    mergedFromCompiledTogether.writeToZipForTesting(out1, OutputMode.DexIndexed);
    ToolHelper.runArtNoVerificationErrors(out1.toString(), testPackage + "." + mainClass);

    Path out2 = temp.newFolder().toPath().resolve("out-separate.zip");
    mergedFromCompiledSeparately.writeToZipForTesting(out2, OutputMode.DexIndexed);
    ToolHelper.runArtNoVerificationErrors(out2.toString(), testPackage + "." + mainClass);

    Path dissasemble1 = temp.newFolder().toPath().resolve("disassemble1.txt");
    Path dissasemble2 = temp.newFolder().toPath().resolve("disassemble2.txt");
    Disassemble.disassemble(
        DisassembleCommand.builder().addProgramFiles(out1).setOutputPath(dissasemble1).build());
    Disassemble.disassemble(
        DisassembleCommand.builder().addProgramFiles(out2).setOutputPath(dissasemble2).build());
    String content1 = StringUtils.join("\n", Files.readAllLines(dissasemble1));
    String content2 = StringUtils.join("\n", Files.readAllLines(dissasemble2));
    assertEquals(content1, content2);

    Assert.assertArrayEquals(
        readResource(mergedFromCompiledSeparately.getDexProgramResourcesForTesting().get(0)),
        readResource(mergedFromCompiledTogether.getDexProgramResourcesForTesting().get(0)));
  }

  @Test
  public void dexPerClassFileWithDispatchMethods() throws Throwable {
    String testName = "dexPerClassFileWithDispatchMethods";
    String testPackage = "interfacedispatchclasses";
    String mainClass = "TestInterfaceDispatchClasses";

    Path inputJarFile = Paths.get(EXAMPLE_DIR, testPackage + JAR_EXTENSION);

    D8IncrementalTestRunner test = test(testName, testPackage, mainClass);
    test.withInterfaceMethodDesugaring(OffOrAuto.Auto);

    AndroidApp mergedFromCompiledSeparately =
        test.mergeClassFiles(
            Lists.newArrayList(test.compileClassesSeparately(inputJarFile).values()), null);
    AndroidApp mergedFromCompiledTogether =
        test.mergeClassFiles(
            Lists.newArrayList(test.compileClassesTogether(inputJarFile, null).values()), null);

    // TODO(b/123504206): This test throws an index out of bounds exception.
    // Re-write or verify running fails in the expected way.
    Assert.assertArrayEquals(
        readResource(mergedFromCompiledSeparately.getDexProgramResourcesForTesting().get(0)),
        readResource(mergedFromCompiledTogether.getDexProgramResourcesForTesting().get(0)));
  }

  @Test
  public void dexPerClassFileOutputFiles() throws Throwable {
    String testName = "dexPerClassFileNoDesugaring";
    String testPackage = "incremental";
    String mainClass = "IncrementallyCompiled";

    Path out = temp.getRoot().toPath();

    Path inputJarFile = Paths.get(EXAMPLE_DIR, testPackage + JAR_EXTENSION);

    D8IncrementalTestRunner test = test(testName, testPackage, mainClass);
    test.compileClassesTogether(inputJarFile, out);

    String[] topLevelDir = out.toFile().list();
    assert topLevelDir != null;
    assertEquals(1, topLevelDir.length);
    assertEquals("incremental", topLevelDir[0]);

    String[] dexFiles = out.resolve(topLevelDir[0]).toFile().list();
    assert dexFiles != null;
    Arrays.sort(dexFiles);

    String[] expectedFileNames = {
        "IncrementallyCompiled$A$AB.dex",
        "IncrementallyCompiled$A.dex",
        "IncrementallyCompiled$B$BA.dex",
        "IncrementallyCompiled$B.dex",
        "IncrementallyCompiled$C.dex",
        "IncrementallyCompiled.dex"
    };
    Arrays.sort(expectedFileNames);

    Assert.assertArrayEquals(expectedFileNames, dexFiles);
  }

  @Override
  abstract D8IncrementalTestRunner test(String testName, String packageName, String mainClass);

  @Override
  protected void testIntermediateWithMainDexList(
      String packageName, Path input, int expectedMainDexListSize, List<String> mainDexClasses) {
    // Skip those tests.
    Assume.assumeTrue(false);
  }

  @Override
  protected Path buildDexThroughIntermediate(
      String packageName,
      Path input,
      OutputMode outputMode,
      AndroidApiLevel minApi,
      List<String> mainDexClasses) {
    // tests using this should already been skipped.
    throw new Unreachable();
  }

  static byte[] readResource(ProgramResource resource) throws IOException, ResourceException {
    try (InputStream input = resource.getByteStream()) {
      return ByteStreams.toByteArray(input);
    }
  }
}
