// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokeSuperInDefaultInterfaceMethodToNonImmediateInterfaceTest extends TestBase {

  private final boolean includeInterfaceMethodOnJ;

  @Parameterized.Parameters(name = "Include interface method on J: {0}")
  public static Boolean[] data() {
    return BooleanUtils.values();
  }

  public InvokeSuperInDefaultInterfaceMethodToNonImmediateInterfaceTest(
      boolean includeInterfaceMethodOnJ) {
    this.includeInterfaceMethodOnJ = includeInterfaceMethodOnJ;
  }

  @Test
  public void test() throws Exception {
    // Note that the expected output is independent of the presence of J.m().
    String expectedOutput = StringUtils.lines("I.m()", "JImpl.m()");

    byte[] dex = buildProgramDexFileData();
    if (ToolHelper.getDexVm().getVersion().isNewerThan(Version.V6_0_1)) {
      AndroidApp app =
          AndroidApp.builder()
              .addDexProgramData(buildProgramDexFileData(), Origin.unknown())
              .build();
      assertEquals(expectedOutput, runOnArt(app, "TestClass"));
    }

    testForR8(Backend.DEX)
        .addProgramDexFileData(dex)
        .addKeepMainRule("TestClass")
        .setMinApi(AndroidApiLevel.M)
        .run("TestClass")
        .assertSuccessWithOutput(expectedOutput);
  }

  // Using Smali instead of Jasmin because interfaces are broken in Jasmin.
  private byte[] buildProgramDexFileData() throws Exception {
    SmaliBuilder smaliBuilder = new SmaliBuilder();
    smaliBuilder.setMinApi(AndroidApiLevel.N);

    smaliBuilder.addClass("TestClass");

    // public void main(String[] args) { new JImpl().m(); }
    smaliBuilder.addMainMethod(
        1,
        "new-instance v0, LJImpl;",
        "invoke-direct {v0}, LJImpl;-><init>()V",
        "invoke-virtual {v0}, LJImpl;->m()V",
        "return-void");

    smaliBuilder.addInterface("I");

    // default void m() { System.out.println("In I.m()"); }
    smaliBuilder.addInstanceMethod(
        "void",
        "m",
        2,
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"I.m()\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "return-void");

    smaliBuilder.addInterface("J", "java.lang.Object", ImmutableList.of("I"));
    if (includeInterfaceMethodOnJ) {
      smaliBuilder.addInstanceMethod(
          "void",
          "m",
          2,
          "invoke-super {p0}, LI;->m()V",
          "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
          "const-string v1, \"J.m()\"",
          "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
          "return-void");
    }

    smaliBuilder.addClass("JImpl", "java.lang.Object", ImmutableList.of("J"));
    smaliBuilder.addDefaultConstructor();

    // default void m() { I.super.m(); System.out.println("In JImpl.m()"); }
    smaliBuilder.addInstanceMethod(
        "void",
        "m",
        2,
        // Note: invoke-super instruction to the non-immediate interface I.
        "invoke-super {p0}, LI;->m()V",
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"JImpl.m()\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "return-void");

    return smaliBuilder.compile();
  }
}
