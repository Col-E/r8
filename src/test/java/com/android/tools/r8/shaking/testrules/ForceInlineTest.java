// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.testrules;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ForceInlineTest extends TestBase {
  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public ForceInlineTest(Backend backend) {
    this.backend = backend;
  }

  private CodeInspector runTest(List<String> proguardConfiguration) throws Exception {
    ProgramConsumer programConsumer;
    Path library;
    if (backend == Backend.DEX) {
      programConsumer = DexIndexedConsumer.emptyConsumer();
      library = ToolHelper.getDefaultAndroidJar();
    } else {
      assert backend == Backend.CF;
      programConsumer = ClassFileConsumer.emptyConsumer();
      library = ToolHelper.getJava8RuntimeJar();
    }
    R8Command.Builder builder =
        ToolHelper.prepareR8CommandBuilder(
                readClasses(Main.class, A.class, B.class, C.class), programConsumer)
            .addLibraryFiles(library);
    ToolHelper.allowTestProguardOptions(builder);
    builder.addProguardConfiguration(proguardConfiguration, Origin.unknown());
    return new CodeInspector(ToolHelper.runR8(builder.build()));
  }

  @Test
  public void testDefaultInlining() throws Exception {
    CodeInspector inspector =
        runTest(
            ImmutableList.of(
                "-keep class **.Main { *; }",
                "-nevermerge @com.android.tools.r8.NeverMerge class *",
                "-neverinline class *{ @com.android.tools.r8.NeverInline <methods>;}",
                "-dontobfuscate"));

    ClassSubject classA = inspector.clazz(A.class);
    ClassSubject classB = inspector.clazz(B.class);
    ClassSubject classC = inspector.clazz(C.class);
    ClassSubject classMain = inspector.clazz(Main.class);
    assertThat(classA, isPresent());
    assertThat(classB, isPresent());
    assertThat(classC, isPresent());
    assertThat(classMain, isPresent());

    // By default A.m *will not* be inlined (called several times and not small).
    assertThat(classA.method("int", "m", ImmutableList.of("int", "int")), isPresent());
    // By default A.method *will* be inlined (called only once).
    assertThat(classA.method("int", "method", ImmutableList.of()), not(isPresent()));
    // By default B.m *will not* be inlined (called several times and not small).
    assertThat(classB.method("int", "m", ImmutableList.of("int", "int")), isPresent());
    // By default B.method *will* be inlined (called only once).
    assertThat(classB.method("int", "method", ImmutableList.of()), not(isPresent()));
  }

  @Test
  public void testNeverInline() throws Exception {
    CodeInspector inspector =
        runTest(
            ImmutableList.of(
                "-neverinline class **.A { method(); }",
                "-neverinline class **.B { method(); }",
                "-keep class **.Main { *; }",
                "-nevermerge @com.android.tools.r8.NeverMerge class *",
                "-neverinline class *{ @com.android.tools.r8.NeverInline <methods>;}",
                "-dontobfuscate"));

    ClassSubject classA = inspector.clazz(A.class);
    ClassSubject classB = inspector.clazz(B.class);
    ClassSubject classC = inspector.clazz(C.class);
    ClassSubject classMain = inspector.clazz(Main.class);
    assertThat(classA, isPresent());
    assertThat(classB, isPresent());
    assertThat(classC, isPresent());
    assertThat(classMain, isPresent());

    // Compared to the default method is no longer inlined.
    assertThat(classA.method("int", "m", ImmutableList.of("int", "int")), isPresent());
    assertThat(classA.method("int", "method", ImmutableList.of()), isPresent());
    assertThat(classB.method("int", "m", ImmutableList.of("int", "int")), isPresent());
    assertThat(classB.method("int", "method", ImmutableList.of()), isPresent());
  }

  @Test
  public void testForceInline() throws Exception {
    CodeInspector inspector =
        runTest(
            ImmutableList.of(
                "-forceinline class **.A { int m(int, int); }",
                "-forceinline class **.B { int m(int, int); }",
                "-keep class **.Main { *; }",
                "-nevermerge @com.android.tools.r8.NeverMerge class *",
                "-neverinline class *{ @com.android.tools.r8.NeverInline <methods>;}",
                "-dontobfuscate"));

    ClassSubject classA = inspector.clazz(A.class);
    ClassSubject classB = inspector.clazz(B.class);
    ClassSubject classC = inspector.clazz(C.class);
    ClassSubject classMain = inspector.clazz(Main.class);

    // Compared to the default m is now inlined and method still is, so classes A and B are gone.
    assertThat(classA, not(isPresent()));
    assertThat(classB, not(isPresent()));
    assertThat(classC, isPresent());
    assertThat(classMain, isPresent());
  }

  @Test
  public void testForceInlineFails() throws Exception {
    try {
      CodeInspector inspector =
          runTest(
              ImmutableList.of(
                  "-forceinline class **.A { int x(); }",
                  "-keep class **.Main { *; }",
                  "-nevermerge @com.android.tools.r8.NeverMerge class *",
                  "-dontobfuscate"));
      fail("Force inline of non-inlinable method succeeded");
    } catch (Throwable t) {
      // Ignore assertion error.
    }
  }
}