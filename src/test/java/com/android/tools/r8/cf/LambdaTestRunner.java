// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LambdaTestRunner {

  private static final Class<?> CLASS = LambdaTest.class;
  private static final String METHOD = "main";

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void test() throws Exception {
    // Test that the InvokeDynamic instruction in LambdaTest.main()
    // is not modified by the R8 compilation.
    // First, extract the InvokeDynamic instruction from the input class.
    byte[] inputClass = ToolHelper.getClassAsBytes(CLASS);
    AndroidApp inputApp =
        AndroidApp.builder().addClassProgramData(inputClass, Origin.unknown()).build();
    CfInvokeDynamic insnInput = findFirstInMethod(inputApp);
    Assert.assertNotNull("No CfInvokeDynamic found in input", insnInput);
    // Compile with R8 and extract the InvokeDynamic instruction from the output class.
    AndroidAppConsumers appBuilder = new AndroidAppConsumers();
    Path outPath = temp.getRoot().toPath().resolve("out.jar");
    R8.run(
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setProgramConsumer(appBuilder.wrapClassFileConsumer(new ArchiveConsumer(outPath)))
            .addClassProgramData(inputClass, Origin.unknown())
            .build());
    AndroidApp outputApp = appBuilder.build();
    CfInvokeDynamic insnOutput = findFirstInMethod(outputApp);
    Assert.assertNotNull("No CfInvokeDynamic found in output", insnOutput);
    // Check that the InvokeDynamic instruction is not modified.
    assertEquals(print(insnInput), print(insnOutput));
    // Check that execution gives the same output.
    ProcessResult inputResult =
        ToolHelper.runJava(ToolHelper.getClassPathForTests(), CLASS.getName());
    ProcessResult outputResult = ToolHelper.runJava(outPath, CLASS.getName());
    assertEquals(inputResult.toString(), outputResult.toString());
  }

  private static CfInvokeDynamic findFirstInMethod(AndroidApp app) throws Exception {
    String returnType = "void";
    DexInspector inspector = new DexInspector(app, o -> o.enableCfFrontend = true);
    List<String> args = Collections.singletonList(String[].class.getTypeName());
    DexEncodedMethod method = inspector.clazz(CLASS).method(returnType, METHOD, args).getMethod();
    CfCode code = method.getCode().asCfCode();
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction instanceof CfInvokeDynamic) {
        return (CfInvokeDynamic) instruction;
      }
    }
    return null;
  }

  private static String print(CfInstruction instruction) {
    CfPrinter printer = new CfPrinter();
    instruction.print(printer);
    return printer.toString();
  }

}
