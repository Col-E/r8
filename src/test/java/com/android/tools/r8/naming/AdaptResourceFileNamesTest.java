// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DataResourceProvider.Visitor;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.AdaptResourceFileContentsTest.CustomDataResourceConsumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.KeepingDiagnosticHandler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AdaptResourceFileNamesTest extends ProguardCompatibilityTestBase {

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public AdaptResourceFileNamesTest(Backend backend) {
    this.backend = backend;
  }

  private static final Path CF_DIR =
      Paths.get(ToolHelper.EXAMPLES_CF_DIR).resolve("adaptresourcefilenames");
  private static final Path TEST_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR)
          .resolve("adaptresourcefilenames" + FileUtils.JAR_EXTENSION);

  private KeepingDiagnosticHandler diagnosticsHandler;
  private ClassNameMapper mapper = null;

  @Before
  public void reset() {
    diagnosticsHandler = new KeepingDiagnosticHandler();
    mapper = null;
  }

  private static String getProguardConfig(
      boolean enableAdaptResourceFileNames, String adaptResourceFileNamesPathFilter) {
    String adaptResourceFilenamesRule;
    if (enableAdaptResourceFileNames) {
      adaptResourceFilenamesRule = "-adaptresourcefilenames";
      if (adaptResourceFileNamesPathFilter != null) {
        adaptResourceFilenamesRule += " " + adaptResourceFileNamesPathFilter;
      }
    } else {
      adaptResourceFilenamesRule = "";
    }
    return String.join(
        System.lineSeparator(),
        adaptResourceFilenamesRule,
        "-keep class " + adaptresourcefilenames.TestClass.class.getName() + " {",
        "  public static void main(...);",
        "}");
  }

  private static String getProguardConfigWithNeverInline(
      boolean enableAdaptResourceFileNames, String adaptResourceFileNamesPathFilter) {
    return String.join(
        System.lineSeparator(),
        getProguardConfig(enableAdaptResourceFileNames, adaptResourceFileNamesPathFilter),
        "-neverinline class " + adaptresourcefilenames.A.class.getName() + " {",
        "  public void method();",
        "}",
        "-neverinline class " + adaptresourcefilenames.B.Inner.class.getName() + " {",
        "  public void method();",
        "}");
  }

  @Test
  public void testEnabled() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    compileWithR8(
        getProguardConfigWithNeverInline(true, null), dataResourceConsumer, this::checkR8Renamings);
    // Check that the generated resources have the expected names.
    for (DataEntryResource dataResource : getOriginalDataResources()) {
      assertNotNull(
          "Resource not renamed as expected: " + dataResource.getName(),
          dataResourceConsumer.get(getExpectedRenamingFor(dataResource.getName(), mapper)));
    }
  }

  @Test
  public void testEnabledWithFilter() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    compileWithR8(
        getProguardConfigWithNeverInline(true, "**.md"),
        dataResourceConsumer,
        this::checkR8Renamings);
    // Check that the generated resources have the expected names.
    Map<String, String> expectedRenamings =
        ImmutableMap.of("adaptresourcefilenames/B.md", "adaptresourcefilenames/b.md");
    for (DataEntryResource dataResource : getOriginalDataResources()) {
      assertNotNull(
          "Resource not renamed as expected: " + dataResource.getName(),
          dataResourceConsumer.get(
              expectedRenamings.getOrDefault(dataResource.getName(), dataResource.getName())));
    }
  }

  @Test
  public void testDisabled() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    compileWithR8(getProguardConfigWithNeverInline(false, null), dataResourceConsumer);
    // Check that none of the resources were renamed.
    for (DataEntryResource dataResource : getOriginalDataResources()) {
      assertNotNull(
          "Resource not renamed as expected: " + dataResource.getName(),
          dataResourceConsumer.get(dataResource.getName()));
    }
  }

  @Test
  public void testCollisionBehavior() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    compileWithR8(
        getProguardConfigWithNeverInline(true, null),
        dataResourceConsumer,
        this::checkR8Renamings,
        ImmutableList.<DataEntryResource>builder()
            .addAll(getOriginalDataResources())
            .add(
                DataEntryResource.fromBytes(
                    new byte[0], "adaptresourcefilenames/b.txt", Origin.unknown()))
            .build());
    assertEquals(1, diagnosticsHandler.warnings.size());
    assertThat(
        diagnosticsHandler.warnings.get(0).getDiagnosticMessage(),
        containsString("Resource 'adaptresourcefilenames/b.txt' already exists."));
    assertEquals(getOriginalDataResources().size(), dataResourceConsumer.size());
  }

  @Test
  public void testProguardBehavior() throws Exception {
    Path inputJar = addDataResourcesToExistingJar(TEST_JAR, getOriginalDataResources());
    Path proguardedJar =
        File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    Path proguardMapFile = File.createTempFile("mapping", ".txt", temp.getRoot()).toPath();
    runProguard6Raw(proguardedJar, inputJar, getProguardConfig(true, null), proguardMapFile);
    // Extract the names of the generated resources.
    Set<String> filenames = new HashSet<>();
    ArchiveResourceProvider.fromArchive(proguardedJar, true)
        .accept(
            new Visitor() {
              @Override
              public void visit(DataDirectoryResource directory) {}

              @Override
              public void visit(DataEntryResource file) {
                filenames.add(file.getName());
              }
            });
    // Check that the generated resources have the expected names.
    ClassNameMapper mapper = ClassNameMapper.mapperFromFile(proguardMapFile);
    for (DataEntryResource dataResource : getOriginalDataResources()) {
      String expectedName = getExpectedRenamingFor(dataResource.getName(), mapper);
      assertTrue(
          "Resource not renamed to '" + expectedName + "' as expected: " + dataResource.getName(),
          filenames.contains(expectedName));
    }
  }

  @Test
  public void testProguardCollisionBehavior() throws Exception {
    List<DataEntryResource> originalDataResources = getOriginalDataResources();
    Path inputJar =
        addDataResourcesToExistingJar(
            TEST_JAR,
            ImmutableList.<DataEntryResource>builder()
                .addAll(originalDataResources)
                .add(
                    DataEntryResource.fromBytes(
                        new byte[0], "adaptresourcefilenames/b.txt", Origin.unknown()))
                .build());
    Path proguardedJar =
        File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    runProguard6Raw(
        proguardedJar,
        inputJar,
        getProguardConfig(true, null),
        null,
        processResult -> {
          assertEquals(0, processResult.exitCode);
          assertThat(
              processResult.stderr,
              containsString(
                  "Warning: can't write resource [adaptresourcefilenames/b.txt] "
                      + "(Duplicate jar entry [adaptresourcefilenames/b.txt])"));
        });
    assertEquals(
        originalDataResources.size(),
        getDataResources(ArchiveResourceProvider.fromArchive(proguardedJar, true))
            .stream()
            .filter(dataResource -> !dataResource.getName().equals("META-INF/MANIFEST.MF"))
            .count());
  }

  private AndroidApp compileWithR8(String proguardConfig, DataResourceConsumer dataResourceConsumer)
      throws CompilationFailedException, IOException {
    return compileWithR8(proguardConfig, dataResourceConsumer, null);
  }

  private AndroidApp compileWithR8(
      String proguardConfig,
      DataResourceConsumer dataResourceConsumer,
      StringConsumer proguardMapConsumer)
      throws CompilationFailedException, IOException {
    return compileWithR8(
        proguardConfig, dataResourceConsumer, proguardMapConsumer, getOriginalDataResources());
  }

  private AndroidApp compileWithR8(
      String proguardConfig,
      DataResourceConsumer dataResourceConsumer,
      StringConsumer proguardMapConsumer,
      List<DataEntryResource> dataResources)
      throws CompilationFailedException, IOException {
    R8Command command =
        ToolHelper.allowTestProguardOptions(
                ToolHelper.prepareR8CommandBuilder(
                        getAndroidApp(dataResources), emptyConsumer(backend), diagnosticsHandler)
                    .addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown()))
            .addLibraryFiles(runtimeJar(backend))
            .build();
    return ToolHelper.runR8(
        command,
        options -> {
          // TODO(christofferqa): Class inliner should respect -neverinline.
          options.enableClassInlining = false;
          options.enableVerticalClassMerging = true;
          options.dataResourceConsumer = dataResourceConsumer;
          options.proguardMapConsumer = proguardMapConsumer;
          options.testing.suppressExperimentalCfBackendWarning = true;
        });
  }

  private void checkR8Renamings(String proguardMap, DiagnosticsHandler handler) {
    try {
      // Check that the renamings are as expected. These exact renamings are not important as
      // such, but the test expectations rely on them.
      mapper = ClassNameMapper.mapperFromString(proguardMap);
      assertEquals(
          "adaptresourcefilenames.TestClass",
          mapper.deobfuscateClassName("adaptresourcefilenames.TestClass"));
      assertEquals(
          "adaptresourcefilenames.B", mapper.deobfuscateClassName("adaptresourcefilenames.b"));
      assertEquals(
          "adaptresourcefilenames.B$Inner",
          mapper.deobfuscateClassName("adaptresourcefilenames.a"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private AndroidApp getAndroidApp(List<DataEntryResource> dataResources) throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(ToolHelper.getClassFilesForTestDirectory(CF_DIR));
    dataResources.forEach(builder::addDataResource);
    return builder.build();
  }

  private static List<DataEntryResource> getOriginalDataResources() {
    List<String> filenames =
        ImmutableList.of(
            // Filename with simple name in root directory.
            "TestClass",
            "B",
            // Filename with qualified name in root directory.
            "adaptresourcefilenames.TestClass",
            "adaptresourcefilenames.B",
            // Filename with qualified directory name in root directory.
            "adaptresourcefilenames/TestClass",
            "adaptresourcefilenames/B",
            // Filename with simple name in sub directory.
            "foo/bar/baz/TestClass",
            "foo/bar/baz/B",
            // Filename with qualified name in sub directory.
            "foo/bar/baz/adaptresourcefiles.TestClass",
            "foo/bar/baz/adaptresourcefiles.B",
            // Filename with qualified directory name in sub directory.
            "foo/bar/baz/adaptresourcefilenames/TestClass",
            "foo/bar/baz/adaptresourcefilenames/B",
            //
            // SUFFIX VARIANTS:
            //
            // Filename with simple name and extension in root directory.
            "TestClass.txt",
            "B.txt",
            // Filename with qualified name and extension in root directory.
            "adaptresourcefilenames.TestClass.txt",
            "adaptresourcefilenames.B.txt",
            // Filename with qualified directory name and extension in root directory.
            "adaptresourcefilenames/TestClass.txt",
            "adaptresourcefilenames/B.txt",
            // Filename with simple name and extension in sub directory.
            "foo/bar/baz/TestClass.txt",
            "foo/bar/baz/B.txt",
            // Filename with qualified name and extension in sub directory.
            "foo/bar/baz/adaptresourcefiles.TestClass.txt",
            "foo/bar/baz/adaptresourcefiles.B.txt",
            // Filename with qualified directory name and extension in sub directory.
            "foo/bar/baz/adaptresourcefilenames/TestClass.txt",
            "foo/bar/baz/adaptresourcefilenames/B.txt",
            // Filename with other extension (used to test filtering).
            "adaptresourcefilenames/TestClass.md",
            "adaptresourcefilenames/B.md",
            // Filename with dot suffix only.
            "adaptresourcefilenames/TestClass.",
            "adaptresourcefilenames/B.",
            // Filename with dot suffix and extension.
            "adaptresourcefilenames/TestClass.suffix.txt",
            "adaptresourcefilenames/B.suffix.txt",
            // Filename with dash suffix and extension.
            "adaptresourcefilenames/TestClass-suffix.txt",
            "adaptresourcefilenames/B-suffix.txt",
            // Filename with dollar suffix and extension.
            "adaptresourcefilenames/TestClass$suffix.txt",
            "adaptresourcefilenames/B$suffix.txt",
            // Filename with dollar suffix matching inner class and extension.
            "adaptresourcefilenames/TestClass$Inner.txt",
            "adaptresourcefilenames/B$Inner.txt",
            // Filename with underscore suffix and extension.
            "adaptresourcefilenames/TestClass_suffix.txt",
            "adaptresourcefilenames/B_suffix.txt",
            // Filename with whitespace suffix and extension.
            "adaptresourcefilenames/TestClass suffix.txt",
            "adaptresourcefilenames/B suffix.txt",
            // Filename with identifier suffix and extension.
            "adaptresourcefilenames/TestClasssuffix.txt",
            "adaptresourcefilenames/Bsuffix.txt",
            // Filename with numeric suffix and extension.
            "adaptresourcefilenames/TestClass42.txt",
            "adaptresourcefilenames/B42.txt",
            //
            // PREFIX VARIANTS:
            //
            // Filename with dot prefix and extension.
            "adaptresourcefilenames/prefix.TestClass.txt",
            "adaptresourcefilenames/prefix.B.txt",
            // Filename with dash prefix and extension.
            "adaptresourcefilenames/prefix-TestClass.txt",
            "adaptresourcefilenames/prefix-B.txt",
            // Filename with dollar prefix and extension.
            "adaptresourcefilenames/prefix$TestClass.txt",
            "adaptresourcefilenames/prefix$B.txt",
            // Filename with identifier prefix and extension.
            "adaptresourcefilenames/prefixTestClass.txt",
            "adaptresourcefilenames/prefixB.txt",
            // Filename with numeric prefix and extension.
            "adaptresourcefilenames/42TestClass.txt",
            "adaptresourcefilenames/42B.txt",
            //
            // PACKAGE RENAMING TESTS:
            //
            // Filename that matches a type, but only the directory should be renamed.
            "adaptresourcefilenames/pkg/C",
            // Filename that matches a type that should be renamed.
            "adaptresourcefilenames/pkg/C.txt",
            // Filename that does not match a type, but where the directory should be renamed.
            "adaptresourcefilenames/pkg/file.txt",
            // Filename that does not match a type, but where a directory-prefix should be renamed.
            "adaptresourcefilenames/pkg/directory/file.txt",
            // Filename that matches a type, but only the directory should be renamed.
            "adaptresourcefilenames/pkg/innerpkg/D",
            // Filename that matches a type that should be renamed.
            "adaptresourcefilenames/pkg/innerpkg/D.txt",
            // Filename that does not match a type, but where the directory should be renamed.
            "adaptresourcefilenames/pkg/innerpkg/file.txt",
            // Filename that does not match a type, but where a directory-prefix should be renamed.
            "adaptresourcefilenames/pkg/innerpkg/directory/file.txt"
            );
    return filenames
        .stream()
        .map(filename -> DataEntryResource.fromBytes(new byte[0], filename, Origin.unknown()))
        .collect(Collectors.toList());
  }

  private static String getExpectedRenamingFor(String filename, ClassNameMapper mapper) {
    String typeName = null;
    String suffix = null;
    switch (filename) {
        // Filename with dot only.
      case "adaptresourcefilenames/B.":
        typeName = "adaptresourcefilenames.B";
        suffix = ".";
        break;
        // Filename with extension.
      case "adaptresourcefilenames/B.txt":
        typeName = "adaptresourcefilenames.B";
        suffix = ".txt";
        break;
        // Filename with other extension (used to test filtering).
      case "adaptresourcefilenames/B.md":
        typeName = "adaptresourcefilenames.B";
        suffix = ".md";
        break;
        // Filename with dot suffix and extension.
      case "adaptresourcefilenames/B.suffix.txt":
        typeName = "adaptresourcefilenames.B";
        suffix = ".suffix.txt";
        break;
        // Filename with dash suffix and extension.
      case "adaptresourcefilenames/B-suffix.txt":
        typeName = "adaptresourcefilenames.B";
        suffix = "-suffix.txt";
        break;
        // Filename with dollar suffix and extension.
      case "adaptresourcefilenames/B$suffix.txt":
        typeName = "adaptresourcefilenames.B";
        suffix = "$suffix.txt";
        break;
        // Filename with dollar suffix matching inner class and extension.
      case "adaptresourcefilenames/B$Inner.txt":
        typeName = "adaptresourcefilenames.B$Inner";
        suffix = ".txt";
        break;
        // Filename with underscore suffix and extension.
      case "adaptresourcefilenames/B_suffix.txt":
        typeName = "adaptresourcefilenames.B";
        suffix = "_suffix.txt";
        break;
        // Filename with whitespace suffix and extension.
      case "adaptresourcefilenames/B suffix.txt":
        typeName = "adaptresourcefilenames.B";
        suffix = " suffix.txt";
        break;
        //
        // PACKAGE RENAMING TESTS
        //
      case "adaptresourcefilenames/pkg/C.txt":
        typeName = "adaptresourcefilenames.pkg.C";
        suffix = ".txt";
        break;
      case "adaptresourcefilenames/pkg/innerpkg/D.txt":
        typeName = "adaptresourcefilenames.pkg.innerpkg.D";
        suffix = ".txt";
        break;
    }
    if (typeName != null) {
      String renamedName = mapper.getObfuscatedToOriginalMapping().inverse().get(typeName);
      assertNotNull(renamedName);
      assertNotEquals(typeName, renamedName);
      return renamedName.replace('.', '/') + suffix;
    }
    // Renamings for files in directories that match packages that have been renamed,
    // but where the filename itself should not be renamed.
    String samePackageAsType = null;
    switch (filename) {
      case "adaptresourcefilenames/pkg/C":
        samePackageAsType = "adaptresourcefilenames.pkg.C";
        suffix = "C";
        break;
      case "adaptresourcefilenames/pkg/file.txt":
        samePackageAsType = "adaptresourcefilenames.pkg.C";
        suffix = "file.txt";
        break;
      case "adaptresourcefilenames/pkg/directory/file.txt":
        samePackageAsType = "adaptresourcefilenames.pkg.C";
        suffix = "directory/file.txt";
        break;
      case "adaptresourcefilenames/pkg/innerpkg/D":
        samePackageAsType = "adaptresourcefilenames.pkg.innerpkg.D";
        suffix = "D";
        break;
      case "adaptresourcefilenames/pkg/innerpkg/file.txt":
        samePackageAsType = "adaptresourcefilenames.pkg.innerpkg.D";
        suffix = "file.txt";
        break;
      case "adaptresourcefilenames/pkg/innerpkg/directory/file.txt":
        samePackageAsType = "adaptresourcefilenames.pkg.innerpkg.D";
        suffix = "directory/file.txt";
        break;
    }
    if (samePackageAsType != null) {
      String renamedName = mapper.getObfuscatedToOriginalMapping().inverse().get(samePackageAsType);
      assertNotNull(renamedName);
      assertNotEquals(samePackageAsType, renamedName);
      if (renamedName.contains(".")) {
        String renamedPackageName = renamedName.substring(0, renamedName.lastIndexOf('.'));
        return renamedPackageName.replace('.', '/') + "/" + suffix;
      }
    }
    return filename;
  }
}
