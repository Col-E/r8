// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldAccessInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberRebindingTest extends TestBase {

  private static final Path JAR_LIBRARY =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR + "memberrebindinglib.jar");

  private final String name;

  private final Backend backend;
  private final String keepRuleFile;
  private final Path programFile;
  private final Consumer<CodeInspector> inspection;
  private final int minApiLevel;

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  public MemberRebindingTest(TestConfiguration configuration) {
    this.name = configuration.name;
    this.backend = configuration.backend;
    this.keepRuleFile = configuration.keepRuleFile;
    this.programFile = configuration.getJarPath();
    this.inspection = configuration.processedInspection;
    this.minApiLevel = configuration.getMinApiLevel();
  }

  @Before
  public void runR8() throws Exception {
    // Generate R8 processed version without library option.
    String out = temp.getRoot().getCanonicalPath();
    // NOTE: It is important to turn off inlining to ensure
    // dex inspection of invokes is predictable.
    R8Command.Builder builder =
        R8Command.builder()
            .setOutput(Paths.get(out), TestBase.outputMode(backend))
            .addLibraryFiles(JAR_LIBRARY, TestBase.runtimeJar(backend))
            .setDisableTreeShaking(true)
            .setDisableMinification(true);
    if (backend == Backend.DEX) {
      builder.setMinApiLevel(minApiLevel);
    }
    if (keepRuleFile != null) {
      builder.addProguardConfigurationFiles(Paths.get(ToolHelper.EXAMPLES_DIR, name, keepRuleFile));
      ToolHelper.allowTestProguardOptions(builder);
    }
    ToolHelper.getAppBuilder(builder).addProgramFiles(programFile);
    ToolHelper.runR8(
        builder.build(),
        options -> {
          options.inlinerOptions().enableInlining = false;
          options.enableRedundantFieldLoadElimination = false;
        });
  }

  private static boolean coolInvokes(InstructionSubject instruction) {
    if (!instruction.isInvokeVirtual() && !instruction.isInvokeInterface() &&
        !instruction.isInvokeStatic()) {
      return false;
    }
    InvokeInstructionSubject invoke = (InvokeInstructionSubject) instruction;
    return !invoke.holder().is("java.io.PrintStream");
  }

  private static void inspectMain(CodeInspector inspector) {
    MethodSubject main = inspector.clazz("memberrebinding.Memberrebinding")
        .method(CodeInspector.MAIN);
    Iterator<InvokeInstructionSubject> iterator =
        main.iterateInstructions(MemberRebindingTest::coolInvokes);
    assertTrue(iterator.next().holder().is("memberrebinding.ClassAtBottomOfChain"));
    assertTrue(iterator.next().holder().is("memberrebinding.ClassAtBottomOfChain"));
    assertTrue(iterator.next().holder().is("memberrebinding.ClassInMiddleOfChain"));
    assertTrue(iterator.next().holder().is("memberrebinding.SuperClassOfAll"));
    assertTrue(iterator.next().holder().is("memberrebinding.ClassExtendsLibraryClass"));
    assertTrue(iterator.next().holder().is("memberrebinding.ClassExtendsLibraryClass"));
    // For the next three - test that we re-bind to library methods (holder is java.util.ArrayList).
    assertTrue(iterator.next().holder().is("java.util.AbstractList"));
    assertTrue(iterator.next().holder().is("java.util.AbstractList"));
    assertTrue(iterator.next().holder().is("java.util.AbstractList"));
    assertTrue(iterator.next().holder().is("memberrebinding.subpackage.PublicClassInTheMiddle"));
    assertTrue(iterator.next().holder().is("memberrebinding.subpackage.PublicClassInTheMiddle"));
    // For the next three - test that we re-bind to the lowest library class.
    assertTrue(iterator.next().holder().is("memberrebindinglib.SubClass"));
    assertTrue(iterator.next().holder().is("memberrebindinglib.SubClass"));
    assertTrue(iterator.next().holder().is("memberrebindinglib.SubClass"));
    // The next one is already precise.
    assertTrue(
        iterator.next().holder().is("memberrebinding.SuperClassOfClassExtendsOtherLibraryClass"));
    // Some dispatches on interfaces.
    assertTrue(iterator.next().holder().is("java.lang.System"));
    assertTrue(iterator.next().holder().is("memberrebindinglib.AnIndependentInterface"));
    // Some dispatches on classes.
    assertTrue(iterator.next().holder().is("java.lang.System"));
    assertTrue(iterator.next().holder().is("memberrebindinglib.SubClass"));
    assertTrue(iterator.next().holder().is("memberrebindinglib.ImplementedInProgramClass"));
    assertFalse(iterator.hasNext());
  }

  private static void inspectMain2(CodeInspector inspector) {
    MethodSubject main = inspector.clazz("memberrebinding2.Memberrebinding")
        .method(CodeInspector.MAIN);
    Iterator<FieldAccessInstructionSubject> iterator =
        main.iterateInstructions(InstructionSubject::isFieldAccess);
    // Run through instance put, static put, instance get and instance get.
    for (int i = 0; i < 4; i++) {
      assertTrue(iterator.next().holder().is("memberrebinding2.ClassAtBottomOfChain"));
      assertTrue(iterator.next().holder().is("memberrebinding2.ClassInMiddleOfChain"));
      assertTrue(iterator.next().holder().is("memberrebinding2.SuperClassOfAll"));
      assertTrue(iterator.next().holder().is("memberrebinding2.subpackage.PublicClass"));
    }
    assertTrue(iterator.next().holder().is("java.lang.System"));
    assertFalse(iterator.hasNext());
  }

  public static MethodSignature TEST =
      new MethodSignature("test", "void", new String[]{});


  private static void inspect3(CodeInspector inspector) {
    MethodSubject main = inspector.clazz("memberrebinding3.Memberrebinding").method(TEST);
    Iterator<InvokeInstructionSubject> iterator =
        main.iterateInstructions(InstructionSubject::isInvoke);
    assertTrue(iterator.next().holder().is("memberrebinding3.ClassAtBottomOfChain"));
    assertTrue(iterator.next().holder().is("memberrebinding3.ClassInMiddleOfChain"));
    assertTrue(iterator.next().holder().is("memberrebinding3.SuperClassOfAll"));
    assertFalse(iterator.hasNext());
  }

  private static void inspect4(CodeInspector inspector) {
    MethodSubject main = inspector.clazz("memberrebinding4.Memberrebinding").method(TEST);
    Iterator<InvokeInstructionSubject> iterator =
        main.iterateInstructions(InstructionSubject::isInvoke);
    assertTrue(iterator.next().holder().is("memberrebinding4.Memberrebinding$Inner"));
    assertTrue(iterator.next().holder().is("memberrebinding4.subpackage.PublicInterface"));
    assertFalse(iterator.hasNext());
  }

  private static class TestConfiguration {

    private enum AndroidVersion {
      PRE_N,
      N
    }

    final String name;
    final Backend backend;
    final String keepRuleFile;
    final AndroidVersion version;
    final Consumer<CodeInspector> processedInspection;

    private TestConfiguration(
        String name,
        Backend backend,
        String keepRuleFile,
        AndroidVersion version,
        Consumer<CodeInspector> processedInspection) {
      this.name = name;
      this.backend = backend;
      this.keepRuleFile = keepRuleFile;
      this.version = version;
      this.processedInspection = processedInspection;
    }

    public static void add(
        ImmutableList.Builder<TestConfiguration> builder,
        String name,
        Backend backend,
        String keepRuleFile,
        AndroidVersion version,
        Consumer<CodeInspector> processedInspection) {
      builder.add(new TestConfiguration(name, backend, keepRuleFile, version, processedInspection));
    }

    public Path getDexPath() {
      return getBuildPath().resolve(name).resolve("classes.dex");
    }

    public Path getJarPath() {
      return getBuildPath().resolve(name + ".jar");
    }

    public Path getBuildPath() {
      switch (version) {
        case PRE_N:
          return Paths.get(ToolHelper.EXAMPLES_BUILD_DIR);
        case N:
          return Paths.get(ToolHelper.EXAMPLES_ANDROID_N_BUILD_DIR);
        default:
          throw new Unreachable();
      }
    }

    public int getMinApiLevel() {
      switch (version) {
        case PRE_N:
          return AndroidApiLevel.getDefault().getLevel();
        case N:
          return AndroidApiLevel.N.getLevel();
        default:
          throw new Unreachable();
      }
    }

    public String toString() {
      return backend + " " + name;
    }
  }

  @Parameters(name = "{0}")
  public static Collection<TestConfiguration> data() {
    ImmutableList.Builder<TestConfiguration> builder = ImmutableList.builder();
    for (Backend backend : ToolHelper.getBackends()) {
      TestConfiguration.add(
          builder,
          "memberrebinding",
          backend,
          null,
          TestConfiguration.AndroidVersion.PRE_N,
          MemberRebindingTest::inspectMain);
      TestConfiguration.add(
          builder,
          "memberrebinding2",
          backend,
          "keep-rules.txt",
          TestConfiguration.AndroidVersion.PRE_N,
          MemberRebindingTest::inspectMain2);
      TestConfiguration.add(
          builder,
          "memberrebinding3",
          backend,
          null,
          TestConfiguration.AndroidVersion.PRE_N,
          MemberRebindingTest::inspect3);
      TestConfiguration.add(
          builder,
          "memberrebinding4",
          backend,
          null,
          TestConfiguration.AndroidVersion.N,
          MemberRebindingTest::inspect4);
    }
    return builder.build();
  }

  @Test
  public void memberRebindingTest() throws IOException {
    Assume.assumeTrue(ToolHelper.artSupported() || ToolHelper.compareAgaintsGoldenFiles());

    Path out = Paths.get(temp.getRoot().getCanonicalPath());
    List<Path> processed;
    if (backend == Backend.DEX) {
      processed = Collections.singletonList(out.resolve("classes.dex"));
    } else {
      assert backend == Backend.CF;
      processed =
          Arrays.stream(out.resolve(name).toFile().listFiles(f -> f.toString().endsWith(".class")))
              .map(File::toPath)
              .collect(Collectors.toList());
    }

    CodeInspector inspector = new CodeInspector(processed);
    inspection.accept(inspector);

  }
}
