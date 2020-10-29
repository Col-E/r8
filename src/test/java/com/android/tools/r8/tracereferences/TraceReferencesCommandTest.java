// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.tracereferences.TraceReferencesFormattingConsumer.OutputFormat;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

  @Test(expected = CompilationFailedException.class)
  public void emptyBuilder() throws Throwable {
    TraceReferencesCommand.builder().build();
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
        "No source specified",
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
        "No source specified",
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

  @Test(expected = CompilationFailedException.class)
  public void invalidFormatCommandLine() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "Unsupported format 'xxx'",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(
                      new String[] {"--format", "xxx"}, Origin.unknown(), handler)
                  .build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void missingFormatCommandLine() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "Missing parameter for --format",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(new String[] {"--format"}, Origin.unknown(), handler)
                  .build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void multipleFormatsCommandLine() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "--format specified multiple times",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(
                      new String[] {"--format", "printuses", "--format", "keep"},
                      Origin.unknown(),
                      handler)
                  .build());
        });
  }

  private String formatName(OutputFormat format) {
    if (format == OutputFormat.PRINTUSAGE) {
      return "printuses";
    }
    if (format == OutputFormat.KEEP_RULES) {
      return "keep";
    }
    assertSame(format, OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION);
    return "keepallowobfuscation";
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
    try {
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
    } catch (CompilationFailedException e) {
      if (diagnosticsCheckerConsumer != null) {
        diagnosticsCheckerConsumer.accept(diagnosticsChecker);
      }
      throw e;
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
    runAndCheckOutput(targetClasses, sourceClasses, format, expected, null);
  }

  public void runAndCheckOutput(
      List<Class<?>> targetClasses,
      List<Class<?>> sourceClasses,
      OutputFormat format,
      String expected,
      Consumer<DiagnosticsChecker> diagnosticsCheckerConsumer)
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
    runAndCheckOutput(targetJar, sourceJar, format, expected, diagnosticsCheckerConsumer);
  }

  @Test
  public void test_printUses() throws Throwable {
    runAndCheckOutput(
        ImmutableList.of(Target.class),
        ImmutableList.of(Source.class),
        OutputFormat.PRINTUSAGE,
        StringUtils.lines(
            ImmutableList.of(
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target",
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target: void"
                    + " method(int)",
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target: int"
                    + " field")));
  }

  @Test
  public void test_keepRules() throws Throwable {
    runAndCheckOutput(
        ImmutableList.of(Target.class),
        ImmutableList.of(Source.class),
        OutputFormat.KEEP_RULES,
        StringUtils.lines(
            ImmutableList.of(
                "-keep class com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target"
                    + " {",
                "  public static void method(int);",
                "  int field;",
                "}",
                "-keeppackagenames com.android.tools.r8.tracereferences")));
  }

  @Test
  public void test_keepRulesAllowObfuscation() throws Throwable {
    runAndCheckOutput(
        ImmutableList.of(Target.class),
        ImmutableList.of(Source.class),
        OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION,
        StringUtils.lines(
            ImmutableList.of(
                "-keep,allowobfuscation class"
                    + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target {",
                "  public static void method(int);",
                "  int field;",
                "}",
                "-keeppackagenames com.android.tools.r8.tracereferences")));
  }

  private void checkTargetMissing(DiagnosticsChecker diagnosticsChecker) {
    Field field;
    Method method;
    try {
      field = Target.class.getField("field");
      method = Target.class.getMethod("method", int.class);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    assertEquals(1, diagnosticsChecker.errors.size());
    assertEquals(0, diagnosticsChecker.warnings.size());
    assertEquals(0, diagnosticsChecker.infos.size());
    diagnosticsChecker.checkErrorsContains(Reference.classFromClass(Target.class).toString());
    diagnosticsChecker.checkErrorsContains(Reference.fieldFromField(field).toString());
    diagnosticsChecker.checkErrorsContains(Reference.methodFromMethod(method).toString());
  }

  @Test
  public void testNoReferences_printUses() throws Throwable {
    try {
      runAndCheckOutput(
          ImmutableList.of(OtherTarget.class),
          ImmutableList.of(Source.class),
          OutputFormat.PRINTUSAGE,
          StringUtils.lines(),
          this::checkTargetMissing);
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testMissingReference_keepRules() throws Throwable {
    Field field = Target.class.getField("field");
    Method method = Target.class.getMethod("method", int.class);
    try {
      runAndCheckOutput(
          ImmutableList.of(OtherTarget.class),
          ImmutableList.of(Source.class),
          OutputFormat.KEEP_RULES,
          StringUtils.lines(),
          this::checkTargetMissing);
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testMissingReference_keepRulesAllowObfuscation() throws Throwable {
    try {
      runAndCheckOutput(
          ImmutableList.of(OtherTarget.class),
          ImmutableList.of(Source.class),
          OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION,
          StringUtils.lines(),
          this::checkTargetMissing);
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testMissingReference_errorToWarning() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar = dir.resolve("target.jar");
    Path sourceJar = dir.resolve("source.jar");
    Path output = dir.resolve("output.txt");
    ZipUtils.zip(
        targetJar,
        ToolHelper.getClassPathForTests(),
        ToolHelper.getClassFileForTestClass(OtherTarget.class));
    ZipUtils.zip(
        sourceJar,
        ToolHelper.getClassPathForTests(),
        ToolHelper.getClassFileForTestClass(Source.class));
    DiagnosticsChecker diagnosticsChecker = new DiagnosticsChecker();
    TraceReferences.run(
        TraceReferencesCommand.parse(
                new String[] {
                  "--lib",
                  ToolHelper.getAndroidJar(AndroidApiLevel.P).toString(),
                  "--target",
                  targetJar.toString(),
                  "--source",
                  sourceJar.toString(),
                  "--output",
                  output.toString(),
                  "--format",
                  formatName(OutputFormat.PRINTUSAGE),
                  "--map-diagnostics:MissingDefinitionsDiagnostic",
                  "error",
                  "warning"
                },
                Origin.unknown(),
                diagnosticsChecker)
            .build());

    assertEquals(0, diagnosticsChecker.errors.size());
    assertEquals(1, diagnosticsChecker.warnings.size());
    assertEquals(0, diagnosticsChecker.infos.size());
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

  private void checkTargetPartlyMissing(DiagnosticsChecker diagnosticsChecker) {
    Field field;
    Method method;
    try {
      field = Target.class.getField("field");
      method = Target.class.getMethod("method", int.class);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    assertEquals(1, diagnosticsChecker.errors.size());
    assertEquals(0, diagnosticsChecker.warnings.size());
    assertEquals(0, diagnosticsChecker.infos.size());
    diagnosticsChecker.checkErrorsContains(Reference.fieldFromField(field).toString());
    diagnosticsChecker.checkErrorsContains(Reference.methodFromMethod(method).toString());
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
    try {
      runAndCheckOutput(
          targetJar,
          sourceJar,
          OutputFormat.PRINTUSAGE,
          StringUtils.lines(
              ImmutableList.of(
                  "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target")),
          this::checkTargetPartlyMissing);
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
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
    try {
      runAndCheckOutput(
          targetJar,
          sourceJar,
          OutputFormat.KEEP_RULES,
          StringUtils.lines(
              ImmutableList.of(
                  "-keep class"
                      + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target"
                      + " {",
                  "}",
                  "-keeppackagenames com.android.tools.r8.tracereferences")),
          this::checkTargetPartlyMissing);
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
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
    try {
      runAndCheckOutput(
          targetJar,
          sourceJar,
          OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION,
          StringUtils.lines(
              ImmutableList.of(
                  "-keep,allowobfuscation class"
                      + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target {",
                  "}",
                  "-keeppackagenames com.android.tools.r8.tracereferences")),
          this::checkTargetPartlyMissing);
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  private byte[] getClassWithTargetRemoved() throws IOException {
    return transformer(Target.class)
        .removeMethods((access, name, descriptor, signature, exceptions) -> name.equals("method"))
        .removeFields((access, name, descriptor, signature, value) -> name.equals("field"))
        .transform();
  }

  static class Target {
    public static int field;

    public static void method(int i) {}
  }

  static class OtherTarget {
    public static void method() {}
  }

  static class Source {
    public static void source() {
      Target.method(Target.field);
    }
  }
}
