// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8Command.Builder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.OffOrAuto;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class D8LazyRunExamplesAndroidOTest
    extends D8IncrementalRunExamplesAndroidOTest {

  // Please note that all tool specific markers have been eliminated in the resulting
  // dex applications. This allows for byte-wise comparison of the results.

  class D8LazyTestRunner extends D8IncrementalTestRunner {

    D8LazyTestRunner(String testName, String packageName, String mainClass) {
      super(testName, packageName, mainClass);
    }

    @Override
    void addClasspathReference(Path testJarFile, D8Command.Builder builder) {
      addClasspathPath(getClassesRoot(testJarFile), builder);
      addClasspathPath(getLegacyClassesRoot(testJarFile), builder);
    }

    private void addClasspathPath(Path location, D8Command.Builder builder) {
      builder.addClasspathResourceProvider(
          DirectoryClassFileProvider.fromDirectory(location.resolve("..")));
    }

    @Override
    void addLibraryReference(Builder builder, Path location) throws IOException {
      builder.addLibraryResourceProvider(new ArchiveClassFileProvider(location));
    }

    @Override
    D8LazyTestRunner self() {
      return this;
    }
  }

  @Override
  D8IncrementalTestRunner test(String testName, String packageName, String mainClass) {
    D8IncrementalTestRunner result = new D8LazyTestRunner(testName, packageName, mainClass);
    result.withOptionConsumer(options -> options.setMarker(null));
    return result;
  }

  @Test
  public void dexPerClassFileWithDesugaringAndFolderClasspath() throws Throwable {
    int minAPILevel = AndroidApiLevel.K.getLevel();
    Path inputFile =
        Paths.get(ToolHelper.EXAMPLES_ANDROID_N_BUILD_DIR, "interfacemethods" + JAR_EXTENSION);
    Path tmpClassesDir = temp.newFolder().toPath();
    ZipUtils.unzip(inputFile.toString(), tmpClassesDir.toFile());
    Path androidJar = ToolHelper.getAndroidJar(minAPILevel);

    // Build all at once.
    AndroidApp fullBuildResult;
    {
      D8Command.Builder command =
          D8Command.builder()
              .setMinApiLevel(minAPILevel)
              .addLibraryFiles(androidJar)
              .addProgramFiles(inputFile);

      fullBuildResult = ToolHelper.runD8(
          command, options -> {
            options.interfaceMethodDesugaring = OffOrAuto.Auto;
            options.setMarker(null);
          });
    }

    // Build each class individually using tmpClassesDir as classpath for desugaring.
    List<ProgramResource> individalDexes = new ArrayList<>();
    List<Path> individualClassFiles =
        Files.walk(tmpClassesDir)
        .filter(classFile -> FileUtils.isClassFile(classFile))
        .collect(Collectors.toList());
    for (Path classFile : individualClassFiles) {
      D8Command.Builder builder =
          D8Command.builder()
              .setMinApiLevel(minAPILevel)
              .addLibraryFiles(androidJar)
              .addClasspathFiles(tmpClassesDir)
              .addProgramFiles(classFile);
      AndroidApp individualResult =
          ToolHelper.runD8(
              builder,
              options -> {
                options.interfaceMethodDesugaring = OffOrAuto.Auto;
                options.setMarker(null);
              });
      individalDexes.add(individualResult.getDexProgramResourcesForTesting().get(0));
    }
    AndroidApp mergedResult = mergeDexResources(minAPILevel, individalDexes);

    assertTrue(Arrays.equals(
        readResource(fullBuildResult.getDexProgramResourcesForTesting().get(0)),
        readResource(mergedResult.getDexProgramResourcesForTesting().get(0))));
  }

  private AndroidApp mergeDexResources(int minAPILevel, List<ProgramResource> individalDexes)
      throws IOException, CompilationException, CompilationFailedException, ResourceException {
    D8Command.Builder builder = D8Command.builder()
        .setMinApiLevel(minAPILevel);
    for (ProgramResource resource : individalDexes) {
      builder.addDexProgramData(readResource(resource), resource.getOrigin());
    }
    AndroidApp mergedResult = ToolHelper.runD8(builder, options -> options.setMarker(null));
    return mergedResult;
  }

}
