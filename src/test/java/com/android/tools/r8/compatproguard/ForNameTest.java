// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.antlr.runtime.RecognitionException;
import org.junit.Test;

public class ForNameTest extends SmaliTestBase {

  private final static String BOO = "Boo";

  @Test
  public void forName_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Example");
    MethodSignature main = builder.addMainMethod(
        1,
        "const-string v0, \"" + BOO + "\"",
        "invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;",
        "move-result-object v0",
        "return-void");

    builder.addClass(BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-keep class Example { *; }",
        "-keep,allowobfuscation class " + BOO,
        "-dontshrink",
        "-dontoptimize");
    Path processedApp = runCompatProguard(builder, pgConfigs);

    DexEncodedMethod method = getMethod(processedApp, main);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
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

  private Path runCompatProguard(SmaliBuilder builder, List<String> proguardConfigurations) {
    try {
      Path dexOutputDir = temp.newFolder().toPath();
      R8Command command =
          new CompatProguardCommandBuilder(true, true)
              .addDexProgramData(builder.compile())
              .setOutputPath(dexOutputDir)
              .addProguardConfiguration(proguardConfigurations)
              .build();
      ToolHelper.runR8(command);
      return dexOutputDir.resolve("classes.dex");
    } catch (CompilationException | IOException | RecognitionException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

}
