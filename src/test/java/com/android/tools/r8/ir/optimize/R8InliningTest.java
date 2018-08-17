// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8InliningTest extends TestBase {

  private Backend backend;

  private static final String DEFAULT_DEX_FILENAME = "classes.dex";
  private static final String DEFAULT_MAP_FILENAME = "proguard.map";

  @Parameters(name = "{0}, backend={1}, minification={2}, allowaccessmodification={3}")
  public static Collection<Object[]> data() {
    List<Object[]> configs = new ArrayList<>();
    for (Backend backend : Backend.values()) {
      for (boolean minification : new Boolean[] {false, true}) {
        for (boolean allowaccessmodification : new Boolean[] {false, true}) {
          configs.add(new Object[] {"Inlining", backend, minification, allowaccessmodification});
        }
      }
    }
    return configs;
  }

  private final String name;
  private final String keepRulesFile;
  private final boolean minification;
  private final boolean allowAccessModification;
  private Path outputDir = null;

  public R8InliningTest(
      String name, Backend backend, boolean minification, boolean allowAccessModification)
      throws IOException {
    this.name = name.toLowerCase();
    this.keepRulesFile = ToolHelper.EXAMPLES_DIR + this.name + "/keep-rules.txt";
    this.backend = backend;
    this.minification = minification;
    this.allowAccessModification = allowAccessModification;
  }

  private Path getInputFile() {
    return Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, name + FileUtils.JAR_EXTENSION);
  }

  private Path getOriginalDexFile() {
    return Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, name, DEFAULT_DEX_FILENAME);
  }

  private Path getGeneratedDexFile() {
    return outputDir.resolve(DEFAULT_DEX_FILENAME);
  }

  private List<Path> getGeneratedFiles(Path dir) {
    if (backend == Backend.DEX) {
      return Collections.singletonList(dir.resolve(Paths.get(DEFAULT_DEX_FILENAME)));
    } else {
      assert backend == Backend.CF;
      return Arrays.stream(
              dir.resolve("inlining").toFile().listFiles(f -> f.toString().endsWith(".class")))
          .map(File::toPath)
          .collect(Collectors.toList());
    }
  }

  private List<Path> getGeneratedFiles() {
    return getGeneratedFiles(outputDir);
  }

  private String getGeneratedProguardMap() throws IOException {
    Path mapFile = outputDir.resolve(DEFAULT_MAP_FILENAME);
    if (Files.exists(mapFile)) {
      return mapFile.toAbsolutePath().toString();
    }
    return null;
  }

  @Rule public ExpectedException thrown = ExpectedException.none();

  private void generateR8Version(Path out, Path mapFile, boolean inlining) throws Exception {
    assert backend == Backend.DEX || backend == Backend.CF;
    R8Command.Builder commandBuilder =
        R8Command.builder()
            .addProgramFiles(getInputFile())
            .setOutput(out, backend == Backend.DEX ? OutputMode.DexIndexed : OutputMode.ClassFile)
            .addProguardConfigurationFiles(Paths.get(keepRulesFile))
            .addLibraryFiles(TestBase.runtimeJar(backend));
    if (mapFile != null) {
      commandBuilder.setProguardMapOutputPath(mapFile);
    }
    if (backend == Backend.DEX) {
      commandBuilder.setMinApiLevel(AndroidApiLevel.M.getLevel());
    }
    if (allowAccessModification) {
      commandBuilder.addProguardConfiguration(
          ImmutableList.of("-allowaccessmodification"), Origin.unknown());
    }
    ToolHelper.runR8(
        commandBuilder.build(),
        o -> {
          // Disable class inlining to prevent that the instantiation of Nullability is removed, and
          // that the class is therefore made abstract.
          o.enableClassInlining = false;
          o.enableMinification = minification;
          o.enableInlining = inlining;
        });
  }

  @Before
  public void generateR8Version() throws Exception {
    outputDir = temp.newFolder().toPath();
    Path mapFile = outputDir.resolve(DEFAULT_MAP_FILENAME);
    generateR8Version(outputDir, mapFile, true);
    String output;
    if (backend == Backend.DEX) {
      output =
          ToolHelper.runArtNoVerificationErrors(
              outputDir.resolve(DEFAULT_DEX_FILENAME).toString(), "inlining.Inlining");
    } else {
      assert backend == Backend.CF;
      output = ToolHelper.runJavaNoVerify(outputDir, "inlining.Inlining").stdout;
    }

    // Compare result with Java to make sure we have the same behavior.
    ProcessResult javaResult = ToolHelper.runJava(getInputFile(), "inlining.Inlining");
    assertEquals(0, javaResult.exitCode);
    assertEquals(javaResult.stdout, output);
  }

  private void checkAbsentBooleanMethod(ClassSubject clazz, String name) {
    checkAbsent(clazz, "boolean", name, Collections.emptyList());
  }

  private void checkAbsent(ClassSubject clazz, String returnType, String name, List<String> args) {
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method(returnType, name, args);
    assertFalse(method.isPresent());
  }

  private void dump(DexEncodedMethod method) {
    System.out.println(method);
    System.out.println(method.codeToString());
  }

  private void dump(Path path, String title) throws Throwable {
    System.out.println(title + ":");
    CodeInspector inspector = new CodeInspector(path.toAbsolutePath());
    inspector.clazz("inlining.Inlining").forAllMethods(m -> dump(m.getMethod()));
    System.out.println(title + " size: " + Files.size(path));
  }

  @Test
  public void checkNoInvokes() throws Throwable {
    Assume.assumeFalse(
        backend == Backend.CF
            && allowAccessModification
            && minification); // TODO(b/112685673) remove when fixed.
    CodeInspector inspector =
        new CodeInspector(getGeneratedFiles(), getGeneratedProguardMap(), null);
    ClassSubject clazz = inspector.clazz("inlining.Inlining");

    // Simple constant inlining.
    checkAbsentBooleanMethod(clazz, "longExpression");
    checkAbsentBooleanMethod(clazz, "intExpression");
    checkAbsentBooleanMethod(clazz, "doubleExpression");
    checkAbsentBooleanMethod(clazz, "floatExpression");
    // Simple return argument inlining.
    checkAbsentBooleanMethod(clazz, "longArgumentExpression");
    checkAbsentBooleanMethod(clazz, "intArgumentExpression");
    checkAbsentBooleanMethod(clazz, "doubleArgumentExpression");
    checkAbsentBooleanMethod(clazz, "floatArgumentExpression");
    // Static method calling interface method. The interface method implementation is in
    // a private class in another package.
    checkAbsent(clazz, "int", "callInterfaceMethod", ImmutableList.of("inlining.IFace"));

    clazz = inspector.clazz("inlining.Nullability");
    checkAbsentBooleanMethod(clazz, "inlinableWithPublicField");
    checkAbsentBooleanMethod(clazz, "inlinableWithControlFlow");
  }

  private long sumOfClassFileSizes(Path dir) {
    long size = 0;
    for (Path p : getGeneratedFiles(dir)) {
      if (ZipUtils.isClassFile(p.toString())) {
        size += p.toFile().length();
      }
    }
    return size;
  }

  @Test
  public void processedFileIsSmaller() throws Throwable {
    Path nonInlinedOutputDir = temp.newFolder().toPath();
    generateR8Version(nonInlinedOutputDir, null, false);

    long nonInlinedSize, inlinedSize;
    if (backend == Backend.DEX) {
      Path nonInlinedDexFile = nonInlinedOutputDir.resolve(DEFAULT_DEX_FILENAME);
      nonInlinedSize = Files.size(nonInlinedDexFile);
      inlinedSize = Files.size(getGeneratedDexFile());
      final boolean ALWAYS_DUMP = false; // Used for debugging.
      if (ALWAYS_DUMP || inlinedSize > nonInlinedSize) {
        dump(nonInlinedDexFile, "No inlining");
        dump(getGeneratedDexFile(), "Inlining enabled");
      }
    } else {
      assert backend == Backend.CF;
      nonInlinedSize = sumOfClassFileSizes(nonInlinedOutputDir);
      inlinedSize = sumOfClassFileSizes(outputDir);
    }
    assertTrue("Inlining failed to reduce size", nonInlinedSize > inlinedSize);
  }

  @Test
  public void invokeOnNullableReceiver() throws Exception {
    Assume.assumeFalse(
        backend == Backend.CF
            && allowAccessModification
            && minification); // TODO(b/112575866) remove when fixed.
    CodeInspector inspector =
        new CodeInspector(getGeneratedFiles(), getGeneratedProguardMap(), null);
    ClassSubject clazz = inspector.clazz("inlining.Nullability");
    MethodSubject m = clazz.method("int", "inlinable", ImmutableList.of("inlining.A"));
    assertEquals(!allowAccessModification, m.isPresent());

    m = clazz.method("int", "notInlinable", ImmutableList.of("inlining.A"));
    assertTrue(m.isPresent());

    m = clazz.method("int", "notInlinableDueToMissingNpe", ImmutableList.of("inlining.A"));
    assertTrue(m.isPresent());

    m = clazz.method("int", "notInlinableDueToSideEffect", ImmutableList.of("inlining.A"));
    assertTrue(m.isPresent());

    m = clazz.method("int", "notInlinableOnThrow", ImmutableList.of("java.lang.Throwable"));
    assertTrue(m.isPresent());

    m =
        clazz.method(
            "int",
            "notInlinableDueToMissingNpeBeforeThrow",
            ImmutableList.of("java.lang.Throwable"));
    assertTrue(m.isPresent());
  }

  @Test
  public void invokeOnNonNullReceiver() throws Exception {
    Assume.assumeFalse(
        backend == Backend.CF
            && allowAccessModification
            && minification); // TODO(b/112685673) remove when fixed.
    CodeInspector inspector =
        new CodeInspector(getGeneratedFiles(), getGeneratedProguardMap(), null);
    ClassSubject clazz = inspector.clazz("inlining.Nullability");
    MethodSubject m = clazz.method("int", "conditionalOperator", ImmutableList.of("inlining.A"));
    assertTrue(m.isPresent());

    // Verify that a.b() is resolved to an inline instance-get.
    Iterator<InstructionSubject> iterator = m.iterateInstructions();
    int instanceGetCount = 0;
    int invokeCount = 0;
    while (iterator.hasNext()) {
      InstructionSubject instruction = iterator.next();
      if (instruction.isInstanceGet()) {
        ++instanceGetCount;
      } else if (instruction.isInvoke()) {
        ++invokeCount;
      }
    }
    assertEquals(1, instanceGetCount);
    assertEquals(0, invokeCount);

    m =
        clazz.method(
            "int",
            "moreControlFlows",
            ImmutableList.of("inlining.A", "inlining.Nullability$Factor"));
    assertTrue(m.isPresent());

    // Verify that a.b() is resolved to an inline instance-get.
    iterator = m.iterateInstructions();
    instanceGetCount = 0;
    invokeCount = 0;
    while (iterator.hasNext()) {
      InstructionSubject instruction = iterator.next();
      if (instruction.isInstanceGet()) {
        ++instanceGetCount;
      } else if (instruction.isInvoke()
          && !((InvokeInstructionSubject) instruction)
              .holder()
              .toString()
              .contains("java.lang.Enum")) {
        ++invokeCount;
      }
    }
    assertEquals(1, instanceGetCount);
    assertEquals(0, invokeCount);
  }
}
