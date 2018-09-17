// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.optimize.string.StringOptimizer.isStringLength;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ForceInline;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class StringLengthTestMain {

  @ForceInline
  static String simpleInlinable() {
    return "Shared";
  }

  public static void main(String[] args) {
    String s1 = "GONE";
    // Can be computed at compile time.
    System.out.println(s1.length());

    String s2 = simpleInlinable();
    // Depends on inlining, and thus R8 can whereas D8 can't.
    System.out.println(s2.length());
    String s3 = simpleInlinable();
    System.out.println(s3);

    String s4 = "Another_shared";
    // Can be computed at compile time.
    System.out.println(s4.length());
    System.out.println(s4);

    String s5 = null;
    try {
      // Cannot be computed at compile time.
      System.out.println(s5.length());
    } catch (NullPointerException npe) {
      // expected
    }

    String s6 = null;
    // Cannot be computed at compile time.
    System.out.println(s6.length());

    String s7 = "\uD800\uDC00";  // U+10000
    // Can be computed at compile time.
    System.out.println(s7.length());
    // Can be computed at compile time.
    System.out.println(s7.codePointCount(0, s7.length()));
    System.out.println(s7);
  }
}

@RunWith(Parameterized.class)
public class StringLengthTest extends TestBase {
  private final Backend backend;
  List<byte[]> classes;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public StringLengthTest(Backend backend) {
    this.backend = backend;
  }

  @Before
  public void setUp() throws Exception {
    classes = ImmutableList.of(
        ToolHelper.getClassAsBytes(ForceInline.class),
        ToolHelper.getClassAsBytes(StringLengthTestMain.class)
    );
  }

  private int countStringLength(Code code) {
    int count = 0;
    if (code.isDexCode()) {
      DexCode dexCode = code.asDexCode();
      for (Instruction instr : dexCode.instructions) {
        if (instr instanceof InvokeVirtual) {
          InvokeVirtual invoke = (InvokeVirtual) instr;
          if (isStringLength(invoke.getMethod(), null)) {
            count++;
          }
        }
      }
      return count;
    }
    assert code.isCfCode();
    CfCode cfCode = code.asCfCode();
    for (CfInstruction instr : cfCode.getInstructions()) {
      if (instr instanceof CfInvoke) {
        CfInvoke invoke = (CfInvoke) instr;
        if (isStringLength(invoke.getMethod(), null)) {
          count++;
        }
      }
    }
    return count;
  }

  private void test(AndroidApp processedApp, int expectedCount) throws Exception {
    String main = StringLengthTestMain.class.getCanonicalName();
    ProcessResult javaOutput = runOnJavaRaw(main, classes, ImmutableList.of());
    assertEquals(1, javaOutput.exitCode);
    assertThat(javaOutput.stderr, containsString("NullPointerException"));

    ProcessResult output =
        backend == Backend.DEX
            ? runOnArtRaw(processedApp, main)
            : runOnJavaRaw(processedApp, main, ImmutableList.of());
    assertEquals(1, output.exitCode);
    assertThat(output.stderr, containsString("NullPointerException"));
    assertEquals(javaOutput.stdout.trim(), output.stdout.trim());

    CodeInspector codeInspector = new CodeInspector(processedApp);
    ClassSubject mainClass = codeInspector.clazz(main);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    int count = countStringLength(mainMethod.getMethod().getCode());
    assertEquals(expectedCount, count);
  }

  @Test
  public void testD8() throws Exception {
    if (backend == Backend.CF) {
      return;
    }

    AndroidApp app = buildAndroidApp(classes);
    AndroidApp processedApp = compileWithD8(app);
    // No inlining, thus the 2nd length() can't be computed.
    test(processedApp, 3);
  }

  @Test
  public void testR8() throws Exception {
    AndroidApp app = buildAndroidApp(classes);
    ProgramConsumer programConsumer;
    Path library;
    if (backend == Backend.DEX) {
      programConsumer = DexIndexedConsumer.emptyConsumer();
      library = ToolHelper.getDefaultAndroidJar();
    } else {
      assert backend == Backend.CF;
      programConsumer = ClassFileConsumer.emptyConsumer();
      library = ToolHelper.getJava8RuntimeJar();
    }
    R8Command.Builder builder =
        ToolHelper.prepareR8CommandBuilder(app, programConsumer).addLibraryFiles(library);
    ToolHelper.allowTestProguardOptions(builder);
    String pgConf = keepMainProguardConfigurationWithForceInlining(StringLengthTestMain.class);
    builder.addProguardConfiguration(ImmutableList.of(pgConf), Origin.unknown());

    AndroidApp processedApp = ToolHelper.runR8(builder.build());
    // length() over null cannot be computed.
    test(processedApp, 2);
  }
}
