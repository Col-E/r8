// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.smali;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.android.tools.r8.utils.dexinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;

public class RemoveWriteOfUnusedFieldsTest extends SmaliTestBase {

  @Test
  public void unreadStaticFieldsRemoved() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    // All these static fields are set but never read.
    builder.addStaticField("booleanField", "Z");
    builder.addStaticField("byteField", "B");
    builder.addStaticField("shortField", "S");
    builder.addStaticField("intField", "I");
    builder.addStaticField("longField", "J");
    builder.addStaticField("floatField", "F");
    builder.addStaticField("doubleField", "D");
    builder.addStaticField("charField", "C");
    builder.addStaticField("objectField", "Ljava/lang/Object;");
    builder.addStaticField("stringField", "Ljava/lang/String;");
    builder.addStaticField("testField", "LTest;");

    builder.addStaticMethod("void", "test", ImmutableList.of(),
        2,
        "const               v0, 0", // single non-float typed zero (ie, int type)
        "sput-boolean        v0, LTest;->booleanField:Z",
        "sput-byte           v0, LTest;->byteField:B",
        "sput-char           v0, LTest;->charField:C",
        "sput-short          v0, LTest;->shortField:S",
        "sput                v0, LTest;->intField:I",
        "const               v0, 0", // float typed zero
        "sput                v0, LTest;->floatField:F",
        "const               v0, 0", // reference typed null
        "sput-object         v0, LTest;->objectField:Ljava/lang/Object;",
        "sput-object         v0, LTest;->stringField:Ljava/lang/String;",
        "sput-object         v0, LTest;->testField:LTest;",
        "const-wide          v0, 0", // wide typed long
        "sput-wide           v0, LTest;->longField:J",
        "const-wide          v0, 0", // wide typed double
        "sput-wide           v0, LTest;->doubleField:D",
        "return-void");

    builder.addMainMethod(
        0,
        "    invoke-static       { }, LTest;->test()V",
        "    return-void                             ");

    AndroidApp input =
        AndroidApp.builder().addDexProgramData(builder.compile(), Origin.unknown()).build();

    Path inputPath = temp.getRoot().toPath().resolve("input.zip");
    input.writeToZip(inputPath, OutputMode.DexIndexed);
    ToolHelper.runArtNoVerificationErrors(inputPath.toString(), DEFAULT_CLASS_NAME);

    AndroidApp app =
        compileWithR8(
            input,
            keepMainProguardConfiguration("Test"),
            options -> options.enableInlining = false);

    DexInspector inspector = new DexInspector(app);
    MethodSubject method = inspector.clazz("Test").method("void", "test", ImmutableList.of());
    DexCode code = method.getMethod().getCode().asDexCode();
    assertTrue(code.isEmptyVoidMethod());
  }

  @Test
  public void unreadInstanceFieldsRemoved() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    // All these instance fields are set but never read.
    builder.addInstanceField("booleanField", "Z");
    builder.addInstanceField("byteField", "B");
    builder.addInstanceField("shortField", "S");
    builder.addInstanceField("intField", "I");
    builder.addInstanceField("longField", "J");
    builder.addInstanceField("floatField", "F");
    builder.addInstanceField("doubleField", "D");
    builder.addInstanceField("charField", "C");
    builder.addInstanceField("objectField", "Ljava/lang/Object;");
    builder.addInstanceField("stringField", "Ljava/lang/String;");
    builder.addInstanceField("testField", "LTest;");

    builder.addInstanceMethod("void", "test", ImmutableList.of(),
        2,
        "const               v0, 0",
        "iput-boolean        v0, p0, LTest;->booleanField:Z",
        "iput-byte           v0, p0, LTest;->byteField:B",
        "iput-char           v0, p0, LTest;->charField:C",
        "iput-short          v0, p0, LTest;->shortField:S",
        "iput                v0, p0, LTest;->intField:I",
        "const               v0, 0",
        "iput                v0, p0, LTest;->floatField:F",
        "const               v0, 0",
        "iput-object         v0, p0, LTest;->objectField:Ljava/lang/Object;",
        "iput-object         v0, p0, LTest;->stringField:Ljava/lang/String;",
        "iput-object         v0, p0, LTest;->testField:LTest;",
        "const-wide          v0, 0",
        "iput-wide           v0, p0, LTest;->longField:J",
        "const-wide          v0, 0",
        "iput-wide           v0, p0, LTest;->doubleField:D",
        "return-void");

    builder.addInitializer(ImmutableList.of(), 0,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "return-void"
    );

    builder.addMainMethod(
        1,
        "    new-instance         v0, LTest;",
        "    invoke-direct        { v0 }, LTest;-><init>()V",
        "    invoke-virtual       { v0 }, LTest;->test()V",
        "    return-void                             ");

    AndroidApp input =
        AndroidApp.builder().addDexProgramData(builder.compile(), Origin.unknown()).build();

    Path inputPath = temp.getRoot().toPath().resolve("input.zip");
    input.writeToZip(inputPath, OutputMode.DexIndexed);
    ToolHelper.runArtNoVerificationErrors(inputPath.toString(), DEFAULT_CLASS_NAME);

    AndroidApp app =
        compileWithR8(
            input,
            keepMainProguardConfiguration("Test"),
            options -> options.enableInlining = false);

    DexInspector inspector = new DexInspector(app);
    MethodSubject method = inspector.clazz("Test").method("void", "test", ImmutableList.of());
    DexCode code = method.getMethod().getCode().asDexCode();
    assertTrue(code.isEmptyVoidMethod());
  }
}
