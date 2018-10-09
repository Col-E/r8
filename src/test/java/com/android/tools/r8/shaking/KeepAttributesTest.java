// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debuginfo.DebugInfoInspector;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.forceproguardcompatibility.keepattributes.TestKeepAttributes;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class KeepAttributesTest extends TestBase {

  public static final Class CLASS = TestKeepAttributes.class;

  @Test
  public void discardAllAttributes()
      throws CompilationFailedException, IOException, ExecutionException {
    List<String> keepRules = ImmutableList.of(
        "-keep class ** { *; }"
    );
    CodeInspector inspector = compile(keepRules);
    DebugInfoInspector debugInfo = debugInfoForMain(inspector);
    checkLineNumbers(false, debugInfo);
    checkLocals(false, debugInfo);
  }

  @Test
  public void keepLineNumberTable()
      throws CompilationFailedException, IOException, ExecutionException {
    List<String> keepRules = ImmutableList.of(
        "-keep class ** { *; }",
        "-keepattributes " + ProguardKeepAttributes.LINE_NUMBER_TABLE
    );
    CodeInspector inspector = compile(keepRules);
    DebugInfoInspector debugInfo = debugInfoForMain(inspector);
    checkLineNumbers(true, debugInfo);
    checkLocals(false, debugInfo);
  }

  @Test
  public void keepLineNumberTableAndLocalVariableTable()
      throws CompilationFailedException, IOException, ExecutionException {
    List<String> keepRules = ImmutableList.of(
        "-keep class ** { *; }",
        "-keepattributes "
            + ProguardKeepAttributes.LINE_NUMBER_TABLE
            + ", "
            + ProguardKeepAttributes.LOCAL_VARIABLE_TABLE
    );
    CodeInspector inspector = compile(keepRules);
    DebugInfoInspector debugInfo = debugInfoForMain(inspector);
    checkLineNumbers(true, debugInfo);
    checkLocals(true, debugInfo);
  }

  @Test
  public void keepLocalVariableTable() throws IOException, ExecutionException {
    List<String> keepRules = ImmutableList.of(
        "-keep class ** { *; }",
        "-keepattributes " + ProguardKeepAttributes.LOCAL_VARIABLE_TABLE
    );
    // Compiling with a keep rule for locals but no line results in an error in R8.
    try {
      compile(keepRules);
    } catch (CompilationFailedException e) {
      assertTrue(e.getCause().getMessage().contains(ProguardKeepAttributes.LOCAL_VARIABLE_TABLE));
      assertTrue(e.getCause().getMessage().contains(ProguardKeepAttributes.LINE_NUMBER_TABLE));
      return;
    }
    fail("Expected error");
  }

  private CodeInspector compile(List<String> keepRules)
      throws CompilationFailedException, IOException, ExecutionException {
    Path dexOut = temp.getRoot().toPath().resolve("dex.zip");
    R8.run(R8Command.builder()
        .setMode(CompilationMode.DEBUG)
        .addProgramFiles(ToolHelper.getClassFileForTestClass(CLASS))
        .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
        .addProguardConfiguration(keepRules, Origin.unknown())
        .setProgramConsumer(new DexIndexedConsumer.ArchiveConsumer(dexOut))
        .build());
    ToolHelper.runArtRaw(dexOut.toString(), CLASS.getCanonicalName());
    return new CodeInspector(dexOut);
  }

  private DebugInfoInspector debugInfoForMain(CodeInspector inspector) {
    return new DebugInfoInspector(
        inspector,
        CLASS.getCanonicalName(),
        new MethodSignature("main", "void", Collections.singleton("java.lang.String[]")));
  }

  private void checkLineNumbers(boolean expected, DebugInfoInspector debugInfo) {
    assertEquals("Expected " + (expected ? "line entries" : "no line entries"),
        expected, debugInfo.getEntries().stream().anyMatch(e -> e.lineEntry));
  }

  private void checkLocals(boolean expected, DebugInfoInspector debugInfo) {
    assertEquals("Expected " + (expected ? "locals" : "no locals"),
        expected, debugInfo.getEntries().stream().anyMatch(e -> !e.locals.isEmpty()));
  }
}
