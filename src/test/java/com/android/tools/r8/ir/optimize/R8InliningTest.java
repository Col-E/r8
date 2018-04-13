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
import com.android.tools.r8.code.AddInt2Addr;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.Goto;
import com.android.tools.r8.code.IfEqz;
import com.android.tools.r8.code.Iget;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.InvokeVirtualRange;
import com.android.tools.r8.code.MoveResult;
import com.android.tools.r8.code.MulInt2Addr;
import com.android.tools.r8.code.PackedSwitch;
import com.android.tools.r8.code.PackedSwitchPayload;
import com.android.tools.r8.code.Return;
import com.android.tools.r8.code.Throw;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
      o.enableMinification = false;
    });
    String artOutput = ToolHelper.runArtNoVerificationErrors(out + "/classes.dex",
        "inlining.Inlining");

    // Compare result with Java to make sure we have the same behavior.
    ProcessResult javaResult = ToolHelper.runJava(getInputFile(), "inlining.Inlining");
    assertEquals(0, javaResult.exitCode);
    assertEquals(javaResult.stdout, artOutput);
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
    MethodSubject m = clazz.method("int", "inlinable", ImmutableList.of("inlining.A"));
    assertTrue(m.isPresent());
    DexCode code = m.getMethod().getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            Iget.class,
            // TODO(b/70572176): below two could be replaced with Iget via inlining
            InvokeVirtualRange.class,
            MoveResult.class,
            AddInt2Addr.class,
            Return.class));

    m = clazz.method("int", "notInlinable", ImmutableList.of("inlining.A"));
    assertTrue(m.isPresent());
    code = m.getMethod().getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            InvokeVirtualRange.class,
            MoveResult.class,
            Iget.class,
            AddInt2Addr.class,
            Return.class));

    m = clazz.method("int", "notInlinableDueToMissingNpe", ImmutableList.of("inlining.A"));
    assertTrue(m.isPresent());
    code = m.getMethod().getCode().asDexCode();
    checkInstructions(code, ImmutableList.of(
        IfEqz.class,
        Iget.class,
        Goto.class,
        Const4.class,
        Return.class));

    m = clazz.method("int", "notInlinableDueToSideEffect", ImmutableList.of("inlining.A"));
    assertTrue(m.isPresent());
    code = m.getMethod().getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            IfEqz.class,
            InvokeVirtualRange.class,
            MoveResult.class,
            Goto.class,
            Iget.class,
            Return.class));

    m = clazz.method("int", "notInlinableOnThrow", ImmutableList.of("java.lang.Throwable"));
    assertTrue(m.isPresent());
    code = m.getMethod().getCode().asDexCode();
    checkInstructions(code, ImmutableList.of(Throw.class));

    m = clazz.method("int", "notInlinableDueToMissingNpeBeforeThrow",
        ImmutableList.of("java.lang.Throwable"));
    assertTrue(m.isPresent());
    code = m.getMethod().getCode().asDexCode();
    checkInstructions(code, ImmutableList.of(
        Throw.class,
        Iget.class,
        Return.class));
  }

  @Test
  public void invokeOnNonNullReceiver() throws Exception {
    DexInspector inspector =
        new DexInspector(getGeneratedDexFile().toAbsolutePath(), getGeneratedProguardMap());
    ClassSubject clazz = inspector.clazz("inlining.Nullability");
    MethodSubject m = clazz.method("int", "conditionalOperator", ImmutableList.of("inlining.A"));
    assertTrue(m.isPresent());
    DexCode code = m.getMethod().getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            IfEqz.class,
            // TODO(b/70794661): below two could be replaced with Iget via inlining if access
            // modification is allowed.
            InvokeVirtualRange.class,
            MoveResult.class,
            Goto.class,
            Const4.class,
            Return.class));

    m = clazz.method("int", "moreControlFlows",
        ImmutableList.of("inlining.A", "inlining.Nullability$Factor"));
    assertTrue(m.isPresent());
    code = m.getMethod().getCode().asDexCode();
    ImmutableList.Builder<Class<? extends Instruction>> builder = ImmutableList.builder();
    // Enum#ordinal
    builder.add(InvokeVirtualRange.class);
    builder.add(MoveResult.class);
    builder.add(PackedSwitch.class);
    for (int i = 0; i < 4; ++i) {
      builder.add(Const4.class);
      builder.add(Goto.class);
    }
    builder.add(Const4.class);
    builder.add(IfEqz.class);
    builder.add(IfEqz.class);
    // TODO(b/70794661): below two could be replaced with Iget via inlining
    builder.add(InvokeVirtualRange.class);
    builder.add(MoveResult.class);
    builder.add(MulInt2Addr.class);
    builder.add(Return.class);
    builder.add(PackedSwitchPayload.class);
    checkInstructions(code, builder.build());
  }

}
