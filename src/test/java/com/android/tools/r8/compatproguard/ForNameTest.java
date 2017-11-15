// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class ForNameTest extends SmaliTestBase {

  private final String CLASS_NAME = "Example";
  private final static String BOO = "Boo";

  @Test
  public void forName_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addMainMethod(
        1,
        "const-string v0, \"" + BOO + "\"",
        "invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;",
        "move-result-object v0",
        "return-void");

    builder.addClass(BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-keep,allowobfuscation class " + BOO,
        "-dontshrink",
        "-dontoptimize");

    DexInspector inspector = runCompatProguard(builder, pgConfigs);

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method(DexInspector.MAIN);
    assertTrue(method.isPresent());

    DexCode code = method.getMethod().getCode().asDexCode();
    // TODO(b/36799092): DeadCodeRemover should be able to remove this instruction.
    assertTrue(code.instructions[0] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[0];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[1] instanceof ConstString);
    constString = (ConstString) code.instructions[1];
    assertNotEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[2] instanceof InvokeStatic);
    assertTrue(code.instructions[3] instanceof ReturnVoid);
  }

  @Test
  public void forName_noMinification() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addMainMethod(
        1,
        "const-string v0, \"" + BOO + "\"",
        "invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;",
        "move-result-object v0",
        "return-void");

    builder.addClass(BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME +" { *; }",
        "-keep class " + BOO,
        "-dontshrink",
        "-dontoptimize",
        "-dontobfuscate");

    DexInspector inspector = runCompatProguard(builder, pgConfigs);

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method(DexInspector.MAIN);
    assertTrue(method.isPresent());

    DexCode code = method.getMethod().getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[0];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[1] instanceof InvokeStatic);
    assertTrue(code.instructions[2] instanceof ReturnVoid);
  }

  private DexInspector runCompatProguard(SmaliBuilder builder, List<String> proguardConfigurations)
      throws Exception{
    Path dexOutputDir = temp.newFolder().toPath();
    R8Command command =
        new CompatProguardCommandBuilder(true, true)
            .addDexProgramData(builder.compile())
            .setOutputPath(dexOutputDir)
            .addProguardConfiguration(proguardConfigurations)
            .build();
    return new DexInspector(ToolHelper.runR8(command));
  }
}
