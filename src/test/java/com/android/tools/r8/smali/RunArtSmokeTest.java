// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.smali;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

public class RunArtSmokeTest extends SmaliTestBase {

  @Test
  public void test() throws Exception {
    // Build simple "Hello, world!" application.
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    MethodSignature mainSignature = builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const-string        v1, \"Hello, world!\"",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "    return-void"
    );

    AndroidApp originalApplication = buildApplication(builder);
    AndroidApp processedApplication = processApplication(originalApplication);
    assertEquals(1, getNumberOfProgramClasses(processedApplication));

    // Return the processed method for inspection.
    DexEncodedMethod main = getMethod(processedApplication, mainSignature);
    assertNotNull(main);

    DexCode code = main.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof SgetObject);
    assertTrue(code.instructions[1] instanceof ConstString);
    assertTrue(code.instructions[2] instanceof InvokeVirtual);
    assertTrue(code.instructions[3] instanceof ReturnVoid);

    // Run the generated code in Art.
    String result = runArt(processedApplication);
    assertEquals(StringUtils.lines("Hello, world!"), result);
  }
}
