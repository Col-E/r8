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
import com.android.tools.r8.code.AddInt2Addr;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.Goto;
import com.android.tools.r8.code.IfEqz;
import com.android.tools.r8.code.Iget;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.code.MoveResult;
import com.android.tools.r8.code.MulInt2Addr;
import com.android.tools.r8.code.PackedSwitch;
import com.android.tools.r8.code.PackedSwitchPayload;
import com.android.tools.r8.code.Return;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.OutputMode;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8InliningTest extends TestBase {

  private static final String DEFAULT_DEX_FILENAME = "classes.dex";
  private static final String DEFAULT_MAP_FILENAME = "proguard.map";

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{{"Inlining"}});
  }

  private final String name;
  private final String keepRulesFile;

  public R8InliningTest(String name) {
    this.name = name.toLowerCase();
    this.keepRulesFile = ToolHelper.EXAMPLES_DIR + this.name + "/keep-rules.txt";
  }

  private Path getInputFile() {
    return Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, name + FileUtils.JAR_EXTENSION);
  }

  private Path getOriginalDexFile() {
    return Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, name, DEFAULT_DEX_FILENAME);
  }

  private Path getGeneratedDexFile() throws IOException {
    return Paths.get(temp.getRoot().getCanonicalPath(), DEFAULT_DEX_FILENAME);
  }

  private String getGeneratedProguardMap() throws IOException {
    Path mapFile = Paths.get(temp.getRoot().getCanonicalPath(), DEFAULT_MAP_FILENAME);
    if (Files.exists(mapFile)) {
      return mapFile.toAbsolutePath().toString();
    }
    return null;
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void generateR8Version() throws Exception {
    Path out = temp.getRoot().toPath();
    R8Command command =
        R8Command.builder()
            .addProgramFiles(getInputFile())
            .setOutput(out, OutputMode.DexIndexed)
            .addProguardConfigurationFiles(Paths.get(keepRulesFile))
            .build();
    // TODO(62048823): Enable minification.
    ToolHelper.runR8(command, o -> {
      o.skipMinification = true;
    });
    ToolHelper.runArtNoVerificationErrors(out + "/classes.dex", "inlining.Inlining");
  }

  private void checkAbsent(ClassSubject clazz, String name) {
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method("boolean", name, Collections.emptyList());
    assertFalse(method.isPresent());
  }

  private void dump(DexEncodedMethod method) {
    System.out.println(method);
    System.out.println(method.codeToString());
  }

  private void dump(Path path, String title) throws Throwable {
    System.out.println(title + ":");
    DexInspector inspector = new DexInspector(path.toAbsolutePath());
    inspector.clazz("inlining.Inlining").forAllMethods(m -> dump(m.getMethod()));
    System.out.println(title + " size: " + Files.size(path));
  }

  @Test
  public void checkNoInvokes() throws Throwable {
    DexInspector inspector = new DexInspector(getGeneratedDexFile().toAbsolutePath(),
        getGeneratedProguardMap());
    ClassSubject clazz = inspector.clazz("inlining.Inlining");
    // Simple constant inlining.
    checkAbsent(clazz, "longExpression");
    checkAbsent(clazz, "intExpression");
    checkAbsent(clazz, "doubleExpression");
    checkAbsent(clazz, "floatExpression");
    // Simple return argument inlining.
    checkAbsent(clazz, "longArgumentExpression");
    checkAbsent(clazz, "intArgumentExpression");
    checkAbsent(clazz, "doubleArgumentExpression");
    checkAbsent(clazz, "floatArgumentExpression");
  }

  @Test
  public void processedFileIsSmaller() throws Throwable {
    long original = Files.size(getOriginalDexFile());
    long generated = Files.size(getGeneratedDexFile());
    final boolean ALWAYS_DUMP = false;  // Used for debugging.
    if (ALWAYS_DUMP || generated > original) {
      dump(getOriginalDexFile(), "Original");
      dump(getGeneratedDexFile(), "Generated");
    }
    assertTrue("Inlining failed to reduce size", original > generated);
  }

  @Test
  public void invokeOnNullableReceiver() throws Exception {
    DexInspector inspector =
        new DexInspector(getGeneratedDexFile().toAbsolutePath(), getGeneratedProguardMap());
    ClassSubject clazz = inspector.clazz("inlining.Nullability");
    MethodSubject m1 = clazz.method("int", "inlinable", ImmutableList.of("inlining.A"));
    assertTrue(m1.isPresent());
    DexCode code = m1.getMethod().getCode().asDexCode();
    assertEquals(5, code.instructions.length);
    assertTrue(code.instructions[0] instanceof Iget);
    // TODO(b/70572176): below two could be replaced with Iget via inlining
    assertTrue(code.instructions[1] instanceof InvokeVirtual);
    assertTrue(code.instructions[2] instanceof MoveResult);
    assertTrue(code.instructions[3] instanceof AddInt2Addr);
    assertTrue(code.instructions[4] instanceof Return);

    MethodSubject m2 = clazz.method("int", "notInlinable", ImmutableList.of("inlining.A"));
    assertTrue(m2.isPresent());
    code = m2.getMethod().getCode().asDexCode();
    assertEquals(5, code.instructions.length);
    assertTrue(code.instructions[0] instanceof InvokeVirtual);
    assertTrue(code.instructions[1] instanceof MoveResult);
    assertTrue(code.instructions[2] instanceof Iget);
    assertTrue(code.instructions[3] instanceof AddInt2Addr);
    assertTrue(code.instructions[4] instanceof Return);
  }

  @Test
  public void invokeOnNonNullReceiver() throws Exception {
    DexInspector inspector =
        new DexInspector(getGeneratedDexFile().toAbsolutePath(), getGeneratedProguardMap());
    ClassSubject clazz = inspector.clazz("inlining.Nullability");
    MethodSubject m1 = clazz.method("int", "conditionalOperator", ImmutableList.of("inlining.A"));
    assertTrue(m1.isPresent());
    DexCode code = m1.getMethod().getCode().asDexCode();
    assertEquals(6, code.instructions.length);
    assertTrue(code.instructions[0] instanceof IfEqz);
    // TODO(b/70794661): below two could be replaced with Iget via inlining
    assertTrue(code.instructions[1] instanceof InvokeVirtual);
    assertTrue(code.instructions[2] instanceof MoveResult);
    assertTrue(code.instructions[3] instanceof Goto);
    assertTrue(code.instructions[4] instanceof Const4);
    assertTrue(code.instructions[5] instanceof Return);

    MethodSubject m2 = clazz.method("int", "moreControlFlows",
        ImmutableList.of("inlining.A", "inlining.Nullability$Factor"));
    assertTrue(m2.isPresent());
    code = m2.getMethod().getCode().asDexCode();
    assertEquals(19, code.instructions.length);
    // Enum#ordinal
    assertTrue(code.instructions[0] instanceof InvokeVirtual);
    assertTrue(code.instructions[1] instanceof MoveResult);
    assertTrue(code.instructions[2] instanceof PackedSwitch);
    for (int i = 3; i < 11; ) {
      assertTrue(code.instructions[i++] instanceof Const4);
      assertTrue(code.instructions[i++] instanceof Goto);
    }
    assertTrue(code.instructions[11] instanceof Const4);
    assertTrue(code.instructions[12] instanceof IfEqz);
    assertTrue(code.instructions[13] instanceof IfEqz);
    // TODO(b/70794661): below two could be replaced with Iget via inlining
    assertTrue(code.instructions[14] instanceof InvokeVirtual);
    assertTrue(code.instructions[15] instanceof MoveResult);
    assertTrue(code.instructions[16] instanceof MulInt2Addr);
    assertTrue(code.instructions[17] instanceof Return);
    assertTrue(code.instructions[18] instanceof PackedSwitchPayload);
  }
}
