// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.tracereferences.TraceReferencesFormattingConsumer.OutputFormat;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import kotlin.text.Charsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesCommandTest extends TestBase {
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public TraceReferencesCommandTest(TestParameters parameters) {}

  @Test
  public void emptyBuilder() throws Throwable {
    verifyEmptyCommand(TraceReferencesCommand.builder().build());
  }

  private void verifyEmptyCommand(TraceReferencesCommand command) {
    assertEquals(0, command.getLibrary().size());
    assertEquals(0, command.getTarget().size());
    assertEquals(0, command.getSource().size());
    assertNull(command.getConsumer());
  }

  @Test(expected = CompilationFailedException.class)
  public void emptyRun() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "No library specified",
        handler -> {
          TraceReferences.run(TraceReferencesCommand.builder(handler).build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void emptyRunCommandLine() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "No library specified",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(new String[] {""}, Origin.unknown(), handler).build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void onlyLibrarySpecified() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "No target specified",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.builder(handler)
                  .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
                  .build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void onlyLibrarySpecifiedCommandLine() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "No target specified",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(
                      new String[] {
                        "--lib", ToolHelper.getAndroidJar(AndroidApiLevel.P).toString()
                      },
                      Origin.unknown(),
                      handler)
                  .build());
        });
  }

  private String formatName(OutputFormat format) {
    if (format == TraceReferencesFormattingConsumer.OutputFormat.PRINTUSAGE) {
      return "printuses";
    }
    if (format == TraceReferencesFormattingConsumer.OutputFormat.KEEP_RULES) {
      return "keep";
    }
    assertSame(
        format, TraceReferencesFormattingConsumer.OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION);
    return "keepallowobfuscation";
  }

  public void runAndCheckOutput(
      Path targetJar, Path sourceJar, OutputFormat format, String expected) throws Throwable {
    runAndCheckOutput(targetJar, sourceJar, format, expected, null);
  }

  public void runAndCheckOutput(
      Path targetJar,
      Path sourceJar,
      OutputFormat format,
      String expected,
      Consumer<DiagnosticsChecker> diagnosticsCheckerConsumer)
      throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path output = dir.resolve("output.txt");
    DiagnosticsChecker diagnosticsChecker = new DiagnosticsChecker();
    TraceReferencesFormattingConsumer consumer = new TraceReferencesFormattingConsumer(format);
    TraceReferences.run(
        TraceReferencesCommand.builder(diagnosticsChecker)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addTargetFiles(targetJar)
            .addSourceFiles(sourceJar)
            .setConsumer(consumer)
            .build());
    assertEquals(expected, consumer.get());
    if (diagnosticsCheckerConsumer != null) {
      diagnosticsCheckerConsumer.accept(diagnosticsChecker);
    } else {
      assertEquals(0, diagnosticsChecker.errors.size());
      assertEquals(0, diagnosticsChecker.warnings.size());
      assertEquals(0, diagnosticsChecker.infos.size());
    }

    TraceReferences.run(
        TraceReferencesCommand.parse(
                new String[] {
                  "--lib", ToolHelper.getAndroidJar(AndroidApiLevel.P).toString(),
                  "--target", targetJar.toString(),
                  "--source", sourceJar.toString(),
                  "--output", output.toString(),
                  "--format", formatName(format)
                },
                Origin.unknown())
            .build());
    assertEquals(expected, FileUtils.readTextFile(output, Charsets.UTF_8));
  }

  public void runAndCheckOutput(
      List<Class<?>> targetClasses,
      List<Class<?>> sourceClasses,
      OutputFormat format,
      String expected)
      throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar = dir.resolve("target.jar");
    Path sourceJar = dir.resolve("source.jar");
    ZipUtils.zip(
        targetJar,
        ToolHelper.getClassPathForTests(),
        targetClasses.stream()
            .map(ToolHelper::getClassFileForTestClass)
            .collect(Collectors.toList()));
    ZipUtils.zip(
        sourceJar,
        ToolHelper.getClassPathForTests(),
        sourceClasses.stream()
            .map(ToolHelper::getClassFileForTestClass)
            .collect(Collectors.toList()));
    runAndCheckOutput(targetJar, sourceJar, format, expected);
  }

  @Test
  public void test_printUses() throws Throwable {
    runAndCheckOutput(
        ImmutableList.of(Target.class),
        ImmutableList.of(Source.class),
        TraceReferencesFormattingConsumer.OutputFormat.PRINTUSAGE,
        StringUtils.lines(
            ImmutableList.of(
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target",
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target: void"
                    + " target(int)",
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target: int"
                    + " field")));
  }

  @Test
  public void test_keepRules() throws Throwable {
    runAndCheckOutput(
        ImmutableList.of(Target.class),
        ImmutableList.of(Source.class),
        TraceReferencesFormattingConsumer.OutputFormat.KEEP_RULES,
        StringUtils.lines(
            ImmutableList.of(
                "-keep class com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target"
                    + " {",
                "  public static void target(int);",
                "  int field;",
                "}",
                "-keeppackagenames com.android.tools.r8.tracereferences")));
  }

  @Test
  public void test_keepRulesAllowObfuscation() throws Throwable {
    runAndCheckOutput(
        ImmutableList.of(Target.class),
        ImmutableList.of(Source.class),
        TraceReferencesFormattingConsumer.OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION,
        StringUtils.lines(
            ImmutableList.of(
                "-keep,allowobfuscation class"
                    + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target {",
                "  public static void target(int);",
                "  int field;",
                "}",
                "-keeppackagenames com.android.tools.r8.tracereferences")));
  }

  @Test
  public void testNoReferences_printUses() throws Throwable {
    runAndCheckOutput(
        ImmutableList.of(OtherTarget.class),
        ImmutableList.of(Source.class),
        TraceReferencesFormattingConsumer.OutputFormat.PRINTUSAGE,
        StringUtils.lines(ImmutableList.of()));
  }

  @Test
  public void testMissingReference_keepRules() throws Throwable {
    runAndCheckOutput(
        ImmutableList.of(OtherTarget.class),
        ImmutableList.of(Source.class),
        TraceReferencesFormattingConsumer.OutputFormat.KEEP_RULES,
        StringUtils.lines(ImmutableList.of()));
  }

  @Test
  public void testNoReferences_keepRulesAllowObfuscation() throws Throwable {
    runAndCheckOutput(
        ImmutableList.of(OtherTarget.class),
        ImmutableList.of(Source.class),
        TraceReferencesFormattingConsumer.OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION,
        StringUtils.lines(ImmutableList.of()));
  }

  public static void zip(Path zipFile, String path, byte[] data) throws IOException {
    try (ZipOutputStream stream =
        new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
      ZipEntry zipEntry = new ZipEntry(path);
      stream.putNextEntry(zipEntry);
      stream.write(data);
      stream.closeEntry();
    }
  }

  @Test
  public void testMissingDefinition_printUses() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar = dir.resolve("target.jar");
    Path sourceJar = dir.resolve("source.jar");
    zip(targetJar, DescriptorUtils.getPathFromJavaType(Target.class), getClassWithTargetRemoved());
    ZipUtils.zip(
        sourceJar,
        ToolHelper.getClassPathForTests(),
        ToolHelper.getClassFileForTestClass(Source.class));
    runAndCheckOutput(
        targetJar,
        sourceJar,
        TraceReferencesFormattingConsumer.OutputFormat.PRINTUSAGE,
        StringUtils.lines(
            ImmutableList.of(
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target",
                "# Error: Could not find definition for method void"
                    + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target"
                    + ".target(int)",
                "# Error: Could not find definition for field int"
                    + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target"
                    + ".field")),
        diagnosticsChecker -> {
          assertEquals(0, diagnosticsChecker.errors.size());
          assertEquals(2, diagnosticsChecker.warnings.size());
          assertEquals(0, diagnosticsChecker.infos.size());
        });
  }

  @Test
  public void testMissingDefinition_keepRules() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar = dir.resolve("target.jar");
    Path sourceJar = dir.resolve("source.jar");
    zip(targetJar, DescriptorUtils.getPathFromJavaType(Target.class), getClassWithTargetRemoved());
    ZipUtils.zip(
        sourceJar,
        ToolHelper.getClassPathForTests(),
        ToolHelper.getClassFileForTestClass(Source.class));
    runAndCheckOutput(
        targetJar,
        sourceJar,
        TraceReferencesFormattingConsumer.OutputFormat.KEEP_RULES,
        StringUtils.lines(
            ImmutableList.of(
                "-keep class com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target"
                    + " {",
                "# Error: Could not find definition for method void"
                    + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target"
                    + ".target(int)",
                "# Error: Could not find definition for field int"
                    + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target"
                    + ".field",
                "}",
                "-keeppackagenames com.android.tools.r8.tracereferences")),
        diagnosticsChecker -> {
          assertEquals(0, diagnosticsChecker.errors.size());
          assertEquals(2, diagnosticsChecker.warnings.size());
          assertEquals(0, diagnosticsChecker.infos.size());
        });
  }

  @Test
  public void testMissingDefinition_keepRulesAllowObfuscation() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar = dir.resolve("target.jar");
    Path sourceJar = dir.resolve("source.jar");
    zip(targetJar, DescriptorUtils.getPathFromJavaType(Target.class), getClassWithTargetRemoved());
    ZipUtils.zip(
        sourceJar,
        ToolHelper.getClassPathForTests(),
        ToolHelper.getClassFileForTestClass(Source.class));
    runAndCheckOutput(
        targetJar,
        sourceJar,
        TraceReferencesFormattingConsumer.OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION,
        StringUtils.lines(
            ImmutableList.of(
                "-keep,allowobfuscation class"
                    + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target {",
                "# Error: Could not find definition for method void"
                    + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target.target(int)",
                "# Error: Could not find definition for field int"
                    + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target.field",
                "}",
                "-keeppackagenames com.android.tools.r8.tracereferences")),
        diagnosticsChecker -> {
          assertEquals(0, diagnosticsChecker.errors.size());
          assertEquals(2, diagnosticsChecker.warnings.size());
          assertEquals(0, diagnosticsChecker.infos.size());
        });
  }

  private byte[] getClassWithTargetRemoved() throws IOException {
    return transformer(Target.class)
        .removeMethods((access, name, descriptor, signature, exceptions) -> name.equals("target"))
        .removeFields((access, name, descriptor, signature, value) -> name.equals("field"))
        .transform();
  }

  static class Target {
    public static int field;

    public static void target(int i) {}
  }

  static class OtherTarget {
    public static void target() {}
  }

  static class Source {
    public static void source() {
      Target.target(Target.field);
    }
  }
}
