// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.devirtualize;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.A;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.A0;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.A1;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.I;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.Main;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokeInterfaceToInvokeVirtualTest extends TestBase {

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public InvokeInterfaceToInvokeVirtualTest(Backend backend) {
    this.backend = backend;
  }

  private AndroidApp runR8(AndroidApp app, Class main, Path out) throws Exception {
    R8Command command =
        ToolHelper.addProguardConfigurationConsumer(
                ToolHelper.prepareR8CommandBuilder(app, emptyConsumer(backend)),
                pgConfig -> {
                  pgConfig.setPrintMapping(true);
                  pgConfig.setPrintMappingFile(out.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE));
                })
            .addProguardConfiguration(
                ImmutableList.of(keepMainProguardConfiguration(main)), Origin.unknown())
            .setOutput(out, outputMode(backend))
            .addLibraryFiles(runtimeJar(backend))
            .build();
    return ToolHelper.runR8(command);
  }

  @Test
  public void listOfInterface() throws Exception {
    byte[][] classes = {
        ToolHelper.getClassAsBytes(I.class),
        ToolHelper.getClassAsBytes(A.class),
        ToolHelper.getClassAsBytes(A0.class),
        ToolHelper.getClassAsBytes(A1.class),
        ToolHelper.getClassAsBytes(Main.class)
    };
    String main = Main.class.getCanonicalName();
    ProcessResult javaOutput = runOnJavaRaw(main, classes);
    assertEquals(0, javaOutput.exitCode);

    AndroidApp originalApp = buildAndroidApp(classes);
    Path out = temp.getRoot().toPath();
    AndroidApp processedApp = runR8(originalApp, Main.class, out);

    CodeInspector codeInspector = new CodeInspector(processedApp);
    ClassSubject clazz = codeInspector.clazz(main);
    MethodSubject m = clazz.method(CodeInspector.MAIN);
    long numOfInvokeInterface =
        Streams.stream(m.iterateInstructions(InstructionSubject::isInvokeInterface)).count();
    // List#add, List#get
    assertEquals(2, numOfInvokeInterface);
    long numOfInvokeVirtual =
        Streams.stream(m.iterateInstructions(InstructionSubject::isInvokeVirtual)).count();
    // System.out.println, I#get ~> A0#get
    assertEquals(2, numOfInvokeVirtual);
    long numOfCast = Streams.stream(m.iterateInstructions(InstructionSubject::isCheckCast)).count();
    // check-cast I ~> check-cast A0
    assertEquals(1, numOfCast);

    ProcessResult output =
        backend == Backend.DEX
            ? runOnArtRaw(processedApp, main)
            : runOnJavaRaw(processedApp, main, Collections.emptyList());
    assertEquals(0, output.exitCode);
    assertEquals(javaOutput.stdout.trim(), output.stdout.trim());
  }
}
