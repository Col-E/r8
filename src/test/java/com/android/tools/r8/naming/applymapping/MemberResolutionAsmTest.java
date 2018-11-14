// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MemberResolutionAsmTest extends AsmTestBase {
  private final Backend backend;

  @Parameterized.Parameters(name = "backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public MemberResolutionAsmTest(Backend backend) {
    this.backend = backend;
  }

  //  class HasMapping { // : X
  //    HasMapping() {
  //      foo();
  //    }
  //
  //    void foo() { // : a
  //      System.out.println("HasMapping#foo");
  //    }
  //  }
  //
  //  class NoMapping extends HasMapping { // : Y
  //    NoMapping() {
  //      super();
  //      foo();
  //    }
  //
  //    private void foo() { // no mapping
  //      System.out.println("NoMapping#foo");
  //    }
  //  }
  //
  //  class NoMappingMain {
  //    public static void main(String[] args) {
  //      new NoMapping();
  //    }
  //  }
  @Test
  public void test_noMapping() throws Exception {
    String main = "NoMappingMain";
    AndroidApp input = buildAndroidApp(
        HasMappingDump.dump(), NoMappingDump.dump(), NoMappingMainDump.dump());

    Path mapPath = temp.newFile("test-mapping.txt").toPath();
    List<String> pgMap = ImmutableList.of(
        "HasMapping -> X:",
        "  void foo() -> a",
        "NoMapping -> Y:"
        // Intentionally missing a mapping for `private` foo().
    );
    FileUtils.writeTextFile(mapPath, pgMap);

    R8Command.Builder builder = ToolHelper.prepareR8CommandBuilder(input, emptyConsumer(backend));
    builder
        .addProguardConfiguration(
            ImmutableList.of(
                keepMainProguardConfiguration(main),
                // Do not turn on -allowaccessmodification
                "-applymapping " + mapPath,
                "-dontobfuscate"), // to use the renamed names in test-mapping.txt
            Origin.unknown())
        .addLibraryFiles(runtimeJar(backend));
    AndroidApp processedApp =
        ToolHelper.runR8(
            builder.build(),
            options -> {
              options.enableInlining = false;
              options.enableVerticalClassMerging = false;
            });

    List<byte[]> classBytes = ImmutableList.of(
        HasMappingDump.dump(), NoMappingDump.dump(), NoMappingMainDump.dump());
    ProcessResult outputBefore = runOnJavaRaw(main, classBytes, ImmutableList.of());
    assertEquals(0, outputBefore.exitCode);
    String outputAfter = runOnVM(processedApp, main, backend);
    assertEquals(outputBefore.stdout.trim(), outputAfter.trim());

    CodeInspector codeInspector = new CodeInspector(processedApp, mapPath);
    ClassSubject base = codeInspector.clazz("HasMapping");
    assertThat(base, isPresent());
    assertThat(base, isRenamed());
    assertEquals("X", base.getFinalName());
    MethodSubject x = base.method("void", "foo", ImmutableList.of());
    assertThat(x, isPresent());
    assertThat(x, isRenamed());
    assertEquals("a", x.getFinalName());

    ClassSubject sub = codeInspector.clazz("NoMapping");
    assertThat(sub, isPresent());
    assertThat(sub, isRenamed());
    assertEquals("Y", sub.getFinalName());
    MethodSubject y = sub.method("void", "foo", ImmutableList.of());
    assertThat(y, isPresent());
    assertThat(y, not(isRenamed()));
    assertEquals("foo", y.getFinalName());
  }

  //  class A { // : X
  //    A() {
  //      x();
  //      y();
  //    }
  //
  //    private void x() { // : y
  //      System.out.println("A#x");
  //    }
  //
  //    public void y() { // : x
  //      System.out.println("A#y");
  //    }
  //  }
  //
  //  class B extends A { // : Y
  //  }
  //
  //  class Main {
  //    public static void main(String[] args) {
  //      new B().x(); // IllegalAccessError
  //    }
  //  }
  @Test
  public void test_swapping() throws Exception {
    String main = "Main";
    AndroidApp input = buildAndroidApp(
        ADump.dump(), BDump.dump(), MainDump.dump());

    Path mapPath = temp.newFile("test-mapping.txt").toPath();
    List<String> pgMap = ImmutableList.of(
        "A -> X:",
        "  void x() -> y",
        "  void y() -> x",
        "B -> Y:"
        // Intentionally missing mappings for non-overridden members
    );
    FileUtils.writeTextFile(mapPath, pgMap);

    R8Command.Builder builder = ToolHelper.prepareR8CommandBuilder(input, emptyConsumer(backend));
    builder
        .addProguardConfiguration(
            ImmutableList.of(
                keepMainProguardConfiguration(main),
                // Do not turn on -allowaccessmodification
                "-applymapping " + mapPath,
                "-dontobfuscate"), // to use the renamed names in test-mapping.txt
            Origin.unknown())
        .addLibraryFiles(runtimeJar(backend));
    AndroidApp processedApp =
        ToolHelper.runR8(
            builder.build(),
            options -> {
              options.enableInlining = false;
              options.enableVerticalClassMerging = false;
            });

    List<byte[]> classBytes = ImmutableList.of(ADump.dump(), BDump.dump(), MainDump.dump());
    ProcessResult outputBefore = runOnJavaRaw(main, classBytes, ImmutableList.of());
    assertNotEquals(0, outputBefore.exitCode);
    String expectedErrorMessage = "IllegalAccessError";
    String expectedErrorSignature = "A.x()V";
    assertThat(outputBefore.stderr, containsString(expectedErrorMessage));
    assertThat(outputBefore.stderr, containsString(expectedErrorSignature));
    ProcessResult outputAfter = runOnVMRaw(processedApp, main, backend);
    assertNotEquals(0, outputAfter.exitCode);
    expectedErrorSignature = "X.y()V";
    if (backend == Backend.DEX) {
      expectedErrorSignature = "void X.y()";
      if (ToolHelper.getDexVm().getVersion().isOlderThanOrEqual(Version.V6_0_1)) {
        expectedErrorMessage ="IncompatibleClassChangeError";
      }
      if (ToolHelper.getDexVm().getVersion().isOlderThanOrEqual(Version.V4_4_4)) {
        expectedErrorMessage ="illegal method access";
        expectedErrorSignature = "LX;.y ()V";
      }
    }
    assertThat(outputAfter.stderr, containsString(expectedErrorMessage));
    assertThat(outputAfter.stderr, containsString(expectedErrorSignature));

    CodeInspector codeInspector = new CodeInspector(processedApp, mapPath);
    ClassSubject base = codeInspector.clazz("A");
    assertThat(base, isPresent());
    assertThat(base, isRenamed());
    assertEquals("X", base.getFinalName());
    MethodSubject x = base.method("void", "x", ImmutableList.of());
    assertThat(x, isPresent());
    assertThat(x, isRenamed());
    assertEquals("y", x.getFinalName());

    ClassSubject sub = codeInspector.clazz("B");
    assertThat(sub, isPresent());
    assertThat(sub, isRenamed());
    assertEquals("Y", sub.getFinalName());
    MethodSubject subX = sub.method("void", "x", ImmutableList.of());
    assertThat(subX, not(isPresent()));
  }
}
