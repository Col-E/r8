// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DataResourceProvider.Visitor;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AdaptResourceFileContentsTest extends ProguardCompatibilityTestBase {

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(
          AdaptResourceFileContentsTestClass.class,
          AdaptResourceFileContentsTestClass.A.class,
          AdaptResourceFileContentsTestClass.B.class);

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public AdaptResourceFileContentsTest(Backend backend) {
    this.backend = backend;
  }

  protected static class CustomDataResourceConsumer implements DataResourceConsumer {

    private final Map<String, ImmutableList<String>> resources = new HashMap<>();

    @Override
    public void accept(DataDirectoryResource directory, DiagnosticsHandler diagnosticsHandler) {
      throw new Unreachable();
    }

    @Override
    public void accept(DataEntryResource file, DiagnosticsHandler diagnosticsHandler) {
      assertFalse(resources.containsKey(file.getName()));
      try {
        byte[] bytes = ByteStreams.toByteArray(file.getByteStream());
        String contents = new String(bytes, Charset.defaultCharset());
        resources.put(file.getName(), ImmutableList.copyOf(contents.split(System.lineSeparator())));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {}

    public ImmutableList<String> get(String name) {
      return resources.get(name);
    }

    public int size() {
      return resources.size();
    }
  }

  private static final ImmutableList<String> originalAllChangedResource =
      ImmutableList.of(
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A<java.lang.String>",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A<"
              + "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A>",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          // Test property values are rewritten.
          "property=com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A",
          // Test XML content is rewritten.
          "<tag>com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A</tag>",
          "<tag attr=\"com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A\"></tag>",
          // Test single-quote literals are rewritten.
          "'com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A'");

  private static final ImmutableList<String> originalAllPresentResource =
      ImmutableList.of(
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B");

  private static final ImmutableList<String> originalAllUnchangedResource =
      ImmutableList.of(
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass",
          // Test there is no renaming for the method on A.
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A.method",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A.method()",
          // Test there is no renaming for the methods on B.
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B.method",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B.method()",
          // Test various prefixes.
          "42com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          "WithIdentifierPrefixcom.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          "-com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          "WithDashPrefix-com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          "$com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          "WithDollarPrefix$com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          ".com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          "WithDotPrefix.com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          // Test various suffixes.
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B42",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$BWithIdentifierSuffix",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B-",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B-WithDashSuffix",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B$",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B$WithDollarSuffix",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B.",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B.WithDotSuffix");

  private static String getProguardConfig(
      boolean enableAdaptResourceFileContents, String adaptResourceFileContentsPathFilter) {
    String adaptResourceFileContentsRule;
    if (enableAdaptResourceFileContents) {
      adaptResourceFileContentsRule = "-adaptresourcefilecontents";
      if (adaptResourceFileContentsPathFilter != null) {
        adaptResourceFileContentsRule += " " + adaptResourceFileContentsPathFilter;
      }
    } else {
      adaptResourceFileContentsRule = "";
    }
    return String.join(
        System.lineSeparator(),
        adaptResourceFileContentsRule,
        "-keep class " + AdaptResourceFileContentsTestClass.class.getName() + " {",
        "  public static void main(...);",
        "}");
  }

  private static String getProguardConfigWithNeverInline(
      boolean enableAdaptResourceFileContents, String adaptResourceFileContentsPathFilter) {
    return String.join(
        System.lineSeparator(),
        getProguardConfig(enableAdaptResourceFileContents, adaptResourceFileContentsPathFilter),
        "-neverinline class com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B {",
        "  public void method();",
        "}");
  }

  @Test
  public void testEnabled() throws Exception {
    String pgConf = getProguardConfigWithNeverInline(true, null);
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    CodeInspector inspector = compileWithR8(pgConf, dataResourceConsumer).inspector();

    // Check that the data resources have changed as expected.
    checkAllAreChanged(
        dataResourceConsumer.get("resource-all-changed.md"), originalAllChangedResource);
    checkAllAreChanged(
        dataResourceConsumer.get("resource-all-changed.txt"), originalAllChangedResource);

    // Check that the new names are consistent with the actual application code.
    checkAllArePresent(dataResourceConsumer.get("resource-all-present.txt"), inspector);

    // Check that the data resources have not changed unexpectedly.
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.class"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.cLaSs"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-unchanged.txt"), originalAllUnchangedResource);
  }

  @Test
  public void testProguardBehavior() throws Exception {
    Path proguardedJar =
        File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    runProguard6Raw(
        proguardedJar,
        ImmutableList.of(
            AdaptResourceFileContentsTestClass.class,
            AdaptResourceFileContentsTestClass.A.class,
            AdaptResourceFileContentsTestClass.B.class),
        getProguardConfig(true, null),
        null,
        getDataResources()
            .stream()
            .filter(x -> !x.getName().toLowerCase().endsWith(FileUtils.CLASS_EXTENSION))
            .collect(Collectors.toList()));

    // Visit each of the resources in the jar and check that their contents are as expected.
    Set<String> filenames = new HashSet<>();
    ArchiveResourceProvider.fromArchive(proguardedJar, true)
        .accept(
            new Visitor() {
              @Override
              public void visit(DataDirectoryResource directory) {}

              @Override
              public void visit(DataEntryResource file) {
                try {
                  byte[] bytes = ByteStreams.toByteArray(file.getByteStream());
                  List<String> lines =
                      Arrays.asList(
                          new String(bytes, Charset.defaultCharset())
                              .split(System.lineSeparator()));
                  if (file.getName().endsWith("resource-all-changed.md")) {
                    checkAllAreChanged(lines, originalAllChangedResource);
                  } else if (file.getName().endsWith("resource-all-changed.txt")) {
                    checkAllAreChanged(lines, originalAllChangedResource);
                  } else if (file.getName().endsWith("resource-all-present.txt")) {
                    checkAllArePresent(lines, new CodeInspector(readJar(proguardedJar)));
                  } else if (file.getName().endsWith("resource-all-unchanged.txt")) {
                    checkAllAreUnchanged(lines, originalAllUnchangedResource);
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }

                // Record that the jar contains a resource with this name.
                filenames.add(file.getName());
              }
            });

    // Check that the jar contains the four expected resources, and nothing else.
    assertEquals(4, filenames.size());
    assertTrue(filenames.stream().anyMatch(x -> x.endsWith("resource-all-changed.md")));
    assertTrue(filenames.stream().anyMatch(x -> x.endsWith("resource-all-changed.txt")));
    assertTrue(filenames.stream().anyMatch(x -> x.endsWith("resource-all-present.txt")));
    assertTrue(filenames.stream().anyMatch(x -> x.endsWith("resource-all-unchanged.txt")));
  }

  @Test
  public void testEnabledWithFilter() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    compileWithR8(getProguardConfigWithNeverInline(true, "*.md"), dataResourceConsumer);

    // Check that the file matching the filter has changed as expected.
    checkAllAreChanged(
        dataResourceConsumer.get("resource-all-changed.md"), originalAllChangedResource);

    // Check that all the other data resources are unchanged.
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.class"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.cLaSs"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.txt"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-present.txt"), originalAllPresentResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-unchanged.txt"), originalAllUnchangedResource);
  }

  @Test
  public void testDisabled() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    compileWithR8(getProguardConfigWithNeverInline(false, null), dataResourceConsumer);

    // Check that all data resources are unchanged.
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.class"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.cLaSs"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.md"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.txt"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-present.txt"), originalAllPresentResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-unchanged.txt"), originalAllUnchangedResource);
  }

  private static void checkAllAreChanged(List<String> adaptedLines, List<String> originalLines) {
    assertEquals(adaptedLines.size(), originalLines.size());
    for (int i = 0; i < originalLines.size(); i++) {
      assertNotEquals(originalLines.get(i), adaptedLines.get(i));
    }
  }

  private static void checkAllArePresent(List<String> lines, CodeInspector inspector) {
    for (String line : lines) {
      assertThat(inspector.clazz(line), isPresent());
    }
  }

  private static void checkAllAreUnchanged(List<String> adaptedLines, List<String> originalLines) {
    assertEquals(adaptedLines.size(), originalLines.size());
    for (int i = 0; i < originalLines.size(); i++) {
      assertEquals(originalLines.get(i), adaptedLines.get(i));
    }
  }

  private TestCompileResult compileWithR8(
      String proguardConfig, DataResourceConsumer dataResourceConsumer)
      throws CompilationFailedException {
    return testForR8(backend)
        .addProgramClasses(CLASSES)
        .addDataResources(getDataResources())
        .enableProguardTestOptions()
        .addKeepRules(proguardConfig)
        .addOptionsModification(
            o -> {
              // TODO(christofferqa): Class inliner should respect -neverinline.
              o.enableClassInlining = false;
              o.enableVerticalClassMerging = true;
              o.dataResourceConsumer = dataResourceConsumer;
            })
        .compile();
  }

  private List<DataEntryResource> getDataResources() {
    return ImmutableList.of(
        DataEntryResource.fromBytes(
            String.join(System.lineSeparator(), originalAllChangedResource).getBytes(),
            "resource-all-changed.class",
            Origin.unknown()),
        DataEntryResource.fromBytes(
            String.join(System.lineSeparator(), originalAllChangedResource).getBytes(),
            "resource-all-changed.cLaSs",
            Origin.unknown()),
        DataEntryResource.fromBytes(
            String.join(System.lineSeparator(), originalAllChangedResource).getBytes(),
            "resource-all-changed.md",
            Origin.unknown()),
        DataEntryResource.fromBytes(
            String.join(System.lineSeparator(), originalAllChangedResource).getBytes(),
            "resource-all-changed.txt",
            Origin.unknown()),
        DataEntryResource.fromBytes(
            String.join(System.lineSeparator(), originalAllPresentResource).getBytes(),
            "resource-all-present.txt",
            Origin.unknown()),
        DataEntryResource.fromBytes(
            String.join(System.lineSeparator(), originalAllUnchangedResource).getBytes(),
            "resource-all-unchanged.txt",
            Origin.unknown()));
  }
}

class AdaptResourceFileContentsTestClass {

  public static void main(String[] args) {
    B obj = new B();
    obj.method();
  }

  static class A {

    public void method() {
      System.out.println("In A.method()");
    }
  }

  static class B extends A {

    public void method() {
      System.out.println("In B.method()");
      super.method();
    }
  }
}
