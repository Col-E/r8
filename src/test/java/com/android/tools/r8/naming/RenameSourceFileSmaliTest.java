// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEvent.SetFile;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests -renamesourcefileattribute.
 */
@RunWith(Parameterized.class)
public class RenameSourceFileSmaliTest extends SmaliTestBase {

  private static final String TEST_FILE = "TestFile.java";

  private static final List<String> DEFAULT_PG_CONFIGS =
      ImmutableList.of(
          "-keep class *** { *; }",
          "-dontoptimize",
          "-keepattributes SourceFile,LineNumberTable");

  private void configure(ProguardConfiguration.Builder pg) {
    if (renaming) {
      pg.setRenameSourceFileAttribute(TEST_FILE);
    }
  }

  @Parameter
  public boolean renaming;

  @Parameters(name="renaming:{0}")
  public static Object[] parameters() {
    return new Object[] {true, false};
  }

  /**
   * replica of {@link RunArtSmokeTest#test}
   */
  @Test
  public void artSmokeTest() throws Exception {
    // Build simple "Hello, world!" application.
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    String originalSourceFile = DEFAULT_CLASS_NAME + FileUtils.JAVA_EXTENSION;
    builder.setSourceFile(originalSourceFile);
    MethodSignature mainSignature = builder.addMainMethod(
        2,
        ".line 1",
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const-string        v1, \"Hello, world!\"",
        ".source \"PrintStream.java\"",
        ".line 337",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        ".source \"" + originalSourceFile + "\"",
        ".line 2",
        "    return-void"
    );
    Path processedApp = runR8(builder, DEFAULT_PG_CONFIGS, this::configure, null);

    DexClass mainClass = getClass(processedApp, DEFAULT_CLASS_NAME);
    verifySourceFileInCodeItem(mainClass, originalSourceFile, TEST_FILE);

    DexEncodedMethod mainMethod = getMethod(processedApp, mainSignature);
    assertNotNull(mainMethod);

    DexCode code = mainMethod.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof SgetObject);
    assertTrue(code.instructions[1] instanceof ConstString);
    assertTrue(code.instructions[2] instanceof InvokeVirtual);
    assertTrue(code.instructions[3] instanceof ReturnVoid);

    // Run the generated code in Art.
    String result = runArt(processedApp, DEFAULT_MAIN_CLASS_NAME);
    assertEquals(StringUtils.lines("Hello, world!"), result);

    verifySourceFileInDebugInfo(code);
  }

  private void verifySourceFileInCodeItem(DexClass clazz, String original, String rename) {
    String processedSourceFile = clazz.sourceFile.toString();
    if (renaming) {
      assertEquals(rename, processedSourceFile);
    } else {
      assertEquals(original, processedSourceFile);
    }
  }

  private void verifySourceFileInDebugInfo(DexCode code) {
    assertNotNull(code.getDebugInfo());
    assertNotEquals(0, code.getDebugInfo().events.length);
    long setFileCount =
        Arrays.stream(code.getDebugInfo().events)
            .filter(dexDebugEvent -> dexDebugEvent instanceof SetFile)
            .count();
    if (renaming) {
      assertEquals(0, setFileCount);
    } else {
      assertNotEquals(0, setFileCount);
    }
  }

}
