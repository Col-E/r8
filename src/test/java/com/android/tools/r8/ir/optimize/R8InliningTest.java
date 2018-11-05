// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
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
      String name, Backend backend, boolean minification, boolean allowAccessModification) {
    this.name = name.toLowerCase();
    this.keepRulesFile = ToolHelper.EXAMPLES_DIR + this.name + "/keep-rules.txt";
    this.backend = backend;
    this.minification = minification;
    this.allowAccessModification = allowAccessModification;
  }

  private Path getInputFile() {
    return Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, name + FileUtils.JAR_EXTENSION);
  }

  private Path getGeneratedDexFile() {
    return outputDir.resolve(DEFAULT_DEX_FILENAME);
  }

  private List<Path> getGeneratedFiles(Path dir) throws IOException {
    if (backend == Backend.DEX) {
      return Collections.singletonList(dir.resolve(Paths.get(DEFAULT_DEX_FILENAME)));
    } else {
      assert backend == Backend.CF;
      return Files.walk(dir)
          .filter(f -> f.toString().endsWith(".class"))
          .collect(Collectors.toList());
    }
  }

  private List<Path> getGeneratedFiles() throws IOException {
    return getGeneratedFiles(outputDir);
  }

  private String getGeneratedProguardMap() {
    Path mapFile = outputDir.resolve(DEFAULT_MAP_FILENAME);
    if (Files.exists(mapFile)) {
      return mapFile.toAbsolutePath().toString();
    }
    return null;
  }

  private void generateR8Version(Path out, Path mapFile, boolean inlining) throws Exception {
    assert backend == Backend.DEX || backend == Backend.CF;
    R8Command.Builder commandBuilder =
        R8Command.builder()
            .addProgramFiles(getInputFile())
            .setOutput(out, outputMode(backend))
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
    ToolHelper.allowTestProguardOptions(commandBuilder);
    ToolHelper.runR8(
        commandBuilder.build(),
        o -> {
          // Disable class inlining to prevent that the instantiation of Nullability is removed, and
          // that the class is therefore made abstract.
          o.enableClassInlining = false;
          o.enableMinification = minification;
          o.enableInlining = inlining;
          o.inliningInstructionLimit = 6;
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

  private long sumOfClassFileSizes(Path dir) throws IOException {
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

  // Count invokes of callee in two code sections in Inlining.main(). The section boundaries are
  // marked with a bunch of calls to the marker0(), marker1() and marker2() methods.
  private static int[] countInvokes(CodeInspector inspector, MethodSubject callee) {
    // 'counters' counts the number of calls (invoke) to callee between marker0/1 and between
    // marker1/2.
    int[] counters = {0, 0};

    if (!callee.isPresent()) {
      // Method is not present, no invokes, only inlined uses possible.
      return counters;
    }

    ClassSubject clazz = inspector.clazz("inlining.Inlining");
    MethodSubject m = clazz.method("void", "main", ImmutableList.of("java.lang.String[]"));
    assertTrue(m.isPresent());

    // Find DexMethods for the marker0, marker1, marker2 methods.
    DexMethod[] markers = new DexMethod[3];
    for (int i = 0; i < 3; ++i) {
      MethodSubject markerSubject = clazz.method("void", "marker" + i, Collections.emptyList());
      assertTrue(markerSubject.isPresent());
      markers[i] = markerSubject.getMethod().method;
    }

    // Count invokes to callee between markers.
    Iterator<InstructionSubject> iterator = m.iterateInstructions();
    int phase = -1;
    while (iterator.hasNext()) {
      InstructionSubject instruction = iterator.next();
      if (!instruction.isInvoke()) {
        continue;
      }

      DexMethod target = ((InvokeInstructionSubject) instruction).invokedMethod();

      if (target == callee.getMethod().method) {
        assertTrue(phase == 0 || phase == 1);
        ++counters[phase];
        continue;
      }

      for (int i = 0; i <= 2; ++i) {
        if (target == markers[i]) {
          assertTrue(phase == i - 1 || phase == i); // Make sure markers found in order.
          phase = i;
          break;
        }
      }
    }
    assertEquals(2, phase);
    return counters;
  }

  private static void assertCounters(int expected0, int expected1, int[] counters) {
    assert counters.length == 2;
    assertEquals(expected0, counters[0]);
    assertEquals(expected1, counters[1]);
  }

  @Test
  public void invokeOnNullableReceiver() throws Exception {
    CodeInspector inspector =
        new CodeInspector(getGeneratedFiles(), getGeneratedProguardMap(), null);

    // These constants describe the expected number of invoke instructions calling a possibly
    // inlined method.
    final int ALWAYS_INLINABLE = 0;
    final int INLINABLE = allowAccessModification ? 0 : 1;
    final int NEVER_INLINABLE = 1;

    ClassSubject clazz = inspector.clazz("inlining.Nullability");
    MethodSubject m;

    m = clazz.method("int", "inlinable", ImmutableList.of("inlining.A"));
    assertCounters(INLINABLE, INLINABLE, countInvokes(inspector, m));

    m = clazz.method("int", "notInlinable", ImmutableList.of("inlining.A"));
    assertCounters(INLINABLE, NEVER_INLINABLE, countInvokes(inspector, m));

    m = clazz.method("int", "notInlinableDueToMissingNpe", ImmutableList.of("inlining.A"));
    assertCounters(INLINABLE, NEVER_INLINABLE, countInvokes(inspector, m));

    m = clazz.method("int", "notInlinableDueToSideEffect", ImmutableList.of("inlining.A"));
    assertCounters(INLINABLE, NEVER_INLINABLE, countInvokes(inspector, m));

    m = clazz.method("int", "notInlinableOnThrow", ImmutableList.of("java.lang.Throwable"));
    assertCounters(ALWAYS_INLINABLE, NEVER_INLINABLE, countInvokes(inspector, m));

    m =
        clazz.method(
            "int",
            "notInlinableDueToMissingNpeBeforeThrow",
            ImmutableList.of("java.lang.Throwable"));
    assertCounters(ALWAYS_INLINABLE, NEVER_INLINABLE * 2, countInvokes(inspector, m));
  }

  @Test
  public void invokeOnNonNullReceiver() throws Exception {
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
