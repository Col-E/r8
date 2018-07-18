// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.devirtualize;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.code.CheckCast;
import com.android.tools.r8.code.InvokeInterface;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.code.InvokeVirtualRange;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.A;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.A0;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.A1;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.I;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.Main;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class InvokeInterfaceToInvokeVirtualTest extends TestBase {

  private AndroidApp runR8(AndroidApp app, Class main, Path out) throws Exception {
    R8Command command =
        ToolHelper.addProguardConfigurationConsumer(
            ToolHelper.prepareR8CommandBuilder(app),
            pgConfig -> {
              pgConfig.setPrintMapping(true);
              pgConfig.setPrintMappingFile(out.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE));
            })
        .addProguardConfiguration(
            ImmutableList.of(keepMainProguardConfiguration(main)),
            Origin.unknown())
        .setOutput(out, OutputMode.DexIndexed)
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
    DexEncodedMethod m = clazz.method(CodeInspector.MAIN).getMethod();
    DexCode code = m.getCode().asDexCode();
    long numOfInvokeInterface = filterInstructionKind(code, InvokeInterface.class).count();
    // List#add, List#get
    assertEquals(2, numOfInvokeInterface);
    long numOfInvokeVirtual =
        filterInstructionKind(code, InvokeVirtual.class).count()
            + filterInstructionKind(code, InvokeVirtualRange.class).count();
    // System.out.println, I#get ~> A0#get
    assertEquals(2, numOfInvokeVirtual);
    long numOfCast = filterInstructionKind(code, CheckCast.class).count();
    // check-cast I ~> check-cast A0
    assertEquals(1, numOfCast);

    ProcessResult artOutput = runOnArtRaw(processedApp, main);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout.trim(), artOutput.stdout.trim());
  }
}
