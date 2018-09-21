// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.optimize.string.StringOptimizer.isStringLength;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.code.Const16;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.ir.code.SingleConstant;
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

  @NeverInline
  static int npe() {
    String n = null;
    // Cannot be computed at compile time.
    return n.length();
  }

  public static void main(String[] args) {
    String s1 = "GONE";
    // Can be computed at compile time: constCount++
    System.out.println(s1.length());

    String s2 = simpleInlinable();
    // Depends on inlining: constCount++
    System.out.println(s2.length());
    String s3 = simpleInlinable();
    System.out.println(s3);

    String s4 = "Another_shared";
    // Can be computed at compile time: constCount++
    System.out.println(s4.length());
    System.out.println(s4);

    String s5 = "\uD800\uDC00";  // U+10000
    // Can be computed at compile time: constCount++
    System.out.println(s5.length());
    // Even reusable: should not increase any counts.
    System.out.println(s5.codePointCount(0, s5.length()));
    System.out.println(s5);

    // Make sure this is not optimized in DEBUG mode.
    int l = "ABC".length();
    System.out.println(l);

    try {
      npe();
    } catch (NullPointerException npe) {
      // expected
    }
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
        ToolHelper.getClassAsBytes(NeverInline.class),
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

  private int countConstNumber(Code code) {
    int count = 0;
    if (code.isDexCode()) {
      DexCode dexCode = code.asDexCode();
      for (Instruction instr : dexCode.instructions) {
        if (instr instanceof Const4 || instr instanceof Const16) {
          int constValue = ((SingleConstant) instr).decodedValue();
          if (constValue != 0) {
            count++;
          }
        }
      }
      return count;
    }
    assert code.isCfCode();
    CfCode cfCode = code.asCfCode();
    for (CfInstruction instr : cfCode.getInstructions()) {
      if (instr instanceof CfConstNumber) {
        CfConstNumber constNumber = (CfConstNumber) instr;
        if (constNumber.getIntValue() != 0) {
          count++;
        }
      }
    }
    return count;
  }

  private void test(
      AndroidApp processedApp,
      int expectedStringLengthCount,
      int expectedConstNumberCount)
      throws Exception {
    String main = StringLengthTestMain.class.getCanonicalName();
    ProcessResult javaOutput = runOnJavaRaw(main, classes, ImmutableList.of());
    assertEquals(0, javaOutput.exitCode);

    ProcessResult output =
        backend == Backend.DEX
            ? runOnArtRaw(processedApp, main)
            : runOnJavaRaw(processedApp, main, ImmutableList.of());
    assertEquals(0, output.exitCode);

    CodeInspector codeInspector = new CodeInspector(processedApp);
    ClassSubject mainClass = codeInspector.clazz(main);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    Code code = mainMethod.getMethod().getCode();
    int count = countStringLength(code);
    assertEquals(expectedStringLengthCount, count);
    count = countConstNumber(code);
    assertEquals(expectedConstNumberCount, count);
  }

  @Test
  public void testD8() throws Exception {
    if (backend == Backend.CF) {
      return;
    }

    AndroidApp app = buildAndroidApp(classes);
    D8Command.Builder builder = ToolHelper.prepareD8CommandBuilder(app);
    builder.setMode(CompilationMode.RELEASE);
    AndroidApp processedApp = ToolHelper.runD8(builder);
    test(processedApp, 1, 4);

    builder = ToolHelper.prepareD8CommandBuilder(app);
    builder.setMode(CompilationMode.DEBUG);
    processedApp = ToolHelper.runD8(builder);
    test(processedApp, 6, 0);
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
    String pgConf = keepMainProguardConfigurationWithInliningAnnotation(StringLengthTestMain.class);
    builder.addProguardConfiguration(ImmutableList.of(pgConf), Origin.unknown());

    AndroidApp processedApp = ToolHelper.runR8(builder.build());
    test(processedApp, 0, 5);
  }
}
