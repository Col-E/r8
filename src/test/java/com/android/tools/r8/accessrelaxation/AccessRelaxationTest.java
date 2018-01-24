// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.accessrelaxation.privatestatic.A;
import com.android.tools.r8.accessrelaxation.privatestatic.B;
import com.android.tools.r8.accessrelaxation.privatestatic.BB;
import com.android.tools.r8.accessrelaxation.privatestatic.C;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class AccessRelaxationTest extends TestBase {
  @Test
  public void testStaticMethodRelaxation() throws Exception {
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFilesForTestPackage(A.class.getPackage()));
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm()));

    // Note: we use '-checkdiscard' to indirectly check that the access relaxation is
    // done which leads to inlining of all pB*** methods so they are removed. Without
    // access relaxation inlining is not performed and method are kept.
    builder.addProguardConfiguration(
        ImmutableList.of(
            "-keep class " + C.class.getCanonicalName() + "{",
            "  public static void main(java.lang.String[]);",
            "}",
            "",
            "-checkdiscard class " + A.class.getCanonicalName() + "{",
            "  *** pBaz();",
            "  *** pBar();",
            "  *** pBar1();",
            "  *** pBlah1();",
            "}",
            "",
            "-checkdiscard class " + B.class.getCanonicalName() + "{",
            "  *** pBlah1();",
            "}",
            "",
            "-checkdiscard class " + BB.class.getCanonicalName() + "{",
            "  *** pBlah1();",
            "}",
            "",
            "-dontobfuscate",
            "-allowaccessmodification"
        ),
        Origin.unknown());

    AndroidApp app = ToolHelper.runR8(builder.build());

    // Run on Jvm.
    String jvmOutput = runOnJava(C.class);

    // Run on Art to check generated code against verifier.
    String artOutput = runOnArt(app, C.class);

    String adjustedArtOutput = artOutput.replace(
        "java.lang.IncompatibleClassChangeError", "java.lang.IllegalAccessError");
    assertEquals(jvmOutput, adjustedArtOutput);
  }
}
