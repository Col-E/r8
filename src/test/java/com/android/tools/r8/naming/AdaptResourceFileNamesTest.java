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
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatabilityTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.KeepingDiagnosticHandler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

public class AdaptResourceFileNamesTest extends ProguardCompatabilityTestBase {

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
        "-keep class " + AdaptResourceFilenamesTestClass.class.getName() + " {",
        "  public static void main(...);",
        "}");
  }

  private static String getProguardConfigWithNeverInline(
      boolean enableAdaptResourceFileNames, String adaptResourceFileNamesPathFilter) {
    return String.join(
        System.lineSeparator(),
        getProguardConfig(enableAdaptResourceFileNames, adaptResourceFileNamesPathFilter),
        "-neverinline class com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB {",
        "  public void method();",
        "}",
        "-neverinline class com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB$Inner {",
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
        ImmutableMap.of(
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB.md",
            "com/android/tools/r8/naming/b.md");
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
                    new byte[0], "com/android/tools/r8/naming/b.txt", Origin.unknown()))
            .build());
    assertEquals(1, diagnosticsHandler.warnings.size());
    assertThat(
        diagnosticsHandler.warnings.get(0).getDiagnosticMessage(),
        containsString("Resource 'com/android/tools/r8/naming/b.txt' already exists."));
    assertEquals(getOriginalDataResources().size(), dataResourceConsumer.size());
  }

  @Test
  public void testProguardBehavior() throws Exception {
    Path proguardedJar =
        File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    Path proguardMapFile = File.createTempFile("mapping", ".txt", temp.getRoot()).toPath();
    runProguard6Raw(
        proguardedJar,
        ImmutableList.of(
            AdaptResourceFilenamesTestClass.class,
            AdaptResourceFilenamesTestClassA.class,
            AdaptResourceFilenamesTestClassB.class,
            AdaptResourceFilenamesTestClassB.Inner.class),
        getProguardConfig(true, null),
        proguardMapFile,
        getOriginalDataResources());
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
    Path proguardedJar =
        File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    List<DataEntryResource> originalDataResources = getOriginalDataResources();
    runProguard6Raw(
        proguardedJar,
        ImmutableList.of(
            AdaptResourceFilenamesTestClass.class,
            AdaptResourceFilenamesTestClassA.class,
            AdaptResourceFilenamesTestClassB.class,
            AdaptResourceFilenamesTestClassB.Inner.class),
        getProguardConfig(true, null),
        null,
        ImmutableList.<DataEntryResource>builder()
            .addAll(originalDataResources)
            .add(
                DataEntryResource.fromBytes(
                    new byte[0], "com/android/tools/r8/naming/b.txt", Origin.unknown()))
            .build(),
        processResult -> {
          assertEquals(0, processResult.exitCode);
          assertThat(
              processResult.stderr,
              containsString(
                  "Warning: can't write resource [com/android/tools/r8/naming/b.txt] "
                      + "(Duplicate jar entry [com/android/tools/r8/naming/b.txt])"));
        });
    assertEquals(
        originalDataResources.size(),
        getDataResources(ArchiveResourceProvider.fromArchive(proguardedJar, true)).size());
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
                ToolHelper.prepareR8CommandBuilder(getAndroidApp(dataResources), diagnosticsHandler)
                    .addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown()))
            .build();
    return ToolHelper.runR8(
        command,
        options -> {
          // TODO(christofferqa): Class inliner should respect -neverinline.
          options.enableClassInlining = false;
          options.enableClassMerging = true;
          options.dataResourceConsumer = dataResourceConsumer;
          options.proguardMapConsumer = proguardMapConsumer;
        });
  }

  private void checkR8Renamings(String proguardMap, DiagnosticsHandler handler) {
    try {
      // Check that the renamings are as expected. These exact renamings are not important as
      // such, but the test expectations rely on them.
      mapper = ClassNameMapper.mapperFromString(proguardMap);
      assertEquals(
          "com.android.tools.r8.naming.AdaptResourceFilenamesTestClass",
          mapper.deobfuscateClassName(
              "com.android.tools.r8.naming.AdaptResourceFilenamesTestClass"));
      assertEquals(
          "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB",
          mapper.deobfuscateClassName("com.android.tools.r8.naming.b"));
      assertEquals(
          "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB$Inner",
          mapper.deobfuscateClassName("com.android.tools.r8.naming.a"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private AndroidApp getAndroidApp(List<DataEntryResource> dataResources) throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(
        ToolHelper.getClassFileForTestClass(AdaptResourceFilenamesTestClass.class),
        ToolHelper.getClassFileForTestClass(AdaptResourceFilenamesTestClassA.class),
        ToolHelper.getClassFileForTestClass(AdaptResourceFilenamesTestClassB.class),
        ToolHelper.getClassFileForTestClass(AdaptResourceFilenamesTestClassB.Inner.class));
    dataResources.forEach(builder::addDataResource);
    return builder.build();
  }

  private static List<DataEntryResource> getOriginalDataResources() {
    List<String> filenames =
        ImmutableList.of(
            // Filename with simple name in root directory.
            "AdaptResourceFilenamesTestClass",
            "AdaptResourceFilenamesTestClassB",
            // Filename with qualified name in root directory.
            "com.android.tools.r8.naming.AdaptResourceFilenamesTestClass",
            "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB",
            // Filename with qualified directory name in root directory.
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClass",
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB",
            // Filename with simple name in sub directory.
            "foo/bar/baz/AdaptResourceFilenamesTestClass",
            "foo/bar/baz/AdaptResourceFilenamesTestClassB",
            // Filename with qualified name in sub directory.
            "foo/bar/baz/com/android.tools.r8.naming.AdaptResourceFilenamesTestClass",
            "foo/bar/baz/com/android.tools.r8.naming.AdaptResourceFilenamesTestClassB",
            // Filename with qualified directory name in sub directory.
            "foo/bar/baz/com/android/tools/r8/naming/AdaptResourceFilenamesTestClass",
            "foo/bar/baz/com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB",
            //
            // SUFFIX VARIANTS:
            //
            // Filename with simple name and extension in root directory.
            "AdaptResourceFilenamesTestClass.txt",
            "AdaptResourceFilenamesTestClassB.txt",
            // Filename with qualified name and extension in root directory.
            "com.android.tools.r8.naming.AdaptResourceFilenamesTestClass.txt",
            "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB.txt",
            // Filename with qualified directory name and extension in root directory.
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClass.txt",
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB.txt",
            // Filename with simple name and extension in sub directory.
            "foo/bar/baz/AdaptResourceFilenamesTestClass.txt",
            "foo/bar/baz/AdaptResourceFilenamesTestClassB.txt",
            // Filename with qualified name and extension in sub directory.
            "foo/bar/baz/com/android.tools.r8.naming.AdaptResourceFilenamesTestClass.txt",
            "foo/bar/baz/com/android.tools.r8.naming.AdaptResourceFilenamesTestClassB.txt",
            // Filename with qualified directory name and extension in sub directory.
            "foo/bar/baz/com/android/tools/r8/naming/AdaptResourceFilenamesTestClass.txt",
            "foo/bar/baz/com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB.txt",
            // Filename with other extension (used to test filtering).
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClass.md",
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB.md",
            // Filename with dot suffix only.
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClass.",
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB.",
            // Filename with dot suffix and extension.
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClass.suffix.txt",
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB.suffix.txt",
            // Filename with dash suffix and extension.
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClass-suffix.txt",
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB-suffix.txt",
            // Filename with dollar suffix and extension.
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClass$suffix.txt",
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB$suffix.txt",
            // Filename with dollar suffix matching inner class and extension.
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClass$Inner.txt",
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB$Inner.txt",
            // Filename with underscore suffix and extension.
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClass_suffix.txt",
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB_suffix.txt",
            // Filename with whitespace suffix and extension.
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClass suffix.txt",
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB suffix.txt",
            // Filename with identifier suffix and extension.
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClasssuffix.txt",
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassBsuffix.txt",
            // Filename with numeric suffix and extension.
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClass42.txt",
            "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB42.txt",
            //
            // PREFIX VARIANTS:
            //
            // Filename with dot prefix and extension.
            "com/android/tools/r8/naming/prefix.AdaptResourceFilenamesTestClass.txt",
            "com/android/tools/r8/naming/prefix.AdaptResourceFilenamesTestClassB.txt",
            // Filename with dash prefix and extension.
            "com/android/tools/r8/naming/prefix-AdaptResourceFilenamesTestClass.txt",
            "com/android/tools/r8/naming/prefix-AdaptResourceFilenamesTestClassB.txt",
            // Filename with dollar prefix and extension.
            "com/android/tools/r8/naming/prefix$AdaptResourceFilenamesTestClass.txt",
            "com/android/tools/r8/naming/prefix$AdaptResourceFilenamesTestClassB.txt",
            // Filename with identifier prefix and extension.
            "com/android/tools/r8/naming/prefixAdaptResourceFilenamesTestClass.txt",
            "com/android/tools/r8/naming/prefixAdaptResourceFilenamesTestClassB.txt",
            // Filename with numeric prefix and extension.
            "com/android/tools/r8/naming/42AdaptResourceFilenamesTestClass.txt",
            "com/android/tools/r8/naming/42AdaptResourceFilenamesTestClassB.txt");
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
      case "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB.":
        typeName = "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB";
        suffix = ".";
        break;
        // Filename with extension.
      case "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB.txt":
        typeName = "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB";
        suffix = ".txt";
        break;
        // Filename with other extension (used to test filtering).
      case "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB.md":
        typeName = "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB";
        suffix = ".md";
        break;
        // Filename with dot suffix and extension.
      case "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB.suffix.txt":
        typeName = "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB";
        suffix = ".suffix.txt";
        break;
        // Filename with dash suffix and extension.
      case "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB-suffix.txt":
        typeName = "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB";
        suffix = "-suffix.txt";
        break;
        // Filename with dollar suffix and extension.
      case "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB$suffix.txt":
        typeName = "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB";
        suffix = "$suffix.txt";
        break;
        // Filename with dollar suffix matching inner class and extension.
      case "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB$Inner.txt":
        typeName = "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB$Inner";
        suffix = ".txt";
        break;
        // Filename with underscore suffix and extension.
      case "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB_suffix.txt":
        typeName = "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB";
        suffix = "_suffix.txt";
        break;
        // Filename with whitespace suffix and extension.
      case "com/android/tools/r8/naming/AdaptResourceFilenamesTestClassB suffix.txt":
        typeName = "com.android.tools.r8.naming.AdaptResourceFilenamesTestClassB";
        suffix = " suffix.txt";
        break;
    }
    if (typeName != null) {
      String renamedName = mapper.getObfuscatedToOriginalMapping().inverse().get(typeName);
      assertNotNull(renamedName);
      assertNotEquals(typeName, renamedName);
      return renamedName.replace('.', '/') + suffix;
    }
    return filename;
  }
}

class AdaptResourceFilenamesTestClass {

  public static void main(String[] args) {
    AdaptResourceFilenamesTestClassB obj = new AdaptResourceFilenamesTestClassB();
    obj.method();
  }
}

class AdaptResourceFilenamesTestClassA {

  public void method() {
    System.out.println("In A.method()");
  }
}

class AdaptResourceFilenamesTestClassB extends AdaptResourceFilenamesTestClassA {

  private Inner inner = new Inner();

  static class Inner {

    public void method() {
      System.out.println("In Inner.method()");
    }
  }

  public void method() {
    System.out.println("In B.method()");
    super.method();
    inner.method();
  }
}
