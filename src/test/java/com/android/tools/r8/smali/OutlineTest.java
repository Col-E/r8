// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.smali;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.dex.code.DexConstWide;
import com.android.tools.r8.dex.code.DexConstWideHigh16;
import com.android.tools.r8.dex.code.DexDivInt;
import com.android.tools.r8.dex.code.DexDivInt2Addr;
import com.android.tools.r8.dex.code.DexGoto;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeStatic;
import com.android.tools.r8.dex.code.DexInvokeStaticRange;
import com.android.tools.r8.dex.code.DexInvokeVirtual;
import com.android.tools.r8.dex.code.DexMoveResult;
import com.android.tools.r8.dex.code.DexMoveResultObject;
import com.android.tools.r8.dex.code.DexMoveResultWide;
import com.android.tools.r8.dex.code.DexReturn;
import com.android.tools.r8.dex.code.DexReturnObject;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.dex.code.DexReturnWide;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Test;

public class OutlineTest extends SmaliTestBase {

  private static ClassReference OUTLINE_CLASS =
      SyntheticItemsTestUtils.syntheticOutlineClass(
          Reference.classFromTypeName(DEFAULT_CLASS_NAME), 0);

  private static final String stringBuilderAppendSignature =
      "Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;";
  private static final String stringBuilderAppendDoubleSignature =
      "Ljava/lang/StringBuilder;->append(D)Ljava/lang/StringBuilder;";
  private static final String stringBuilderAppendIntSignature =
      "Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;";
  private static final String stringBuilderAppendLongSignature =
      "Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;";
  private static final String stringBuilderToStringSignature =
      "Ljava/lang/StringBuilder;->toString()Ljava/lang/String;";

  private Consumer<InternalOptions> configureOptions(Consumer<InternalOptions> optionsConsumer) {
    return options -> {
      // Disable inlining to make sure that code looks as expected.
      options.inlinerOptions().enableInlining = false;
      // Disable string concatenation optimization to not bother outlining of StringBuilder usage.
      options.enableStringConcatenationOptimization = false;
      // Also apply outline options.
      optionsConsumer.accept(options);
    };
  }

  private Consumer<InternalOptions> configureOutlineOptions(
      Consumer<OutlineOptions> optionsConsumer) {
    return options -> {
      // Disable inlining to make sure that code looks as expected.
      options.inlinerOptions().enableInlining = false;
      // Disable the devirtulizer to not remove the super calls
      options.enableDevirtualization = false;
      // Disable string concatenation optimization to not bother outlining of StringBuilder usage.
      options.enableStringConcatenationOptimization = false;
      // Also apply outline options.
      optionsConsumer.accept(options.outline);
    };
  }

  private String firstOutlineMethodName() {
    return OUTLINE_CLASS.getTypeName() + '.' + SyntheticItemsTestUtils.syntheticMethodName();
  }

  private static boolean isOutlineMethodName(DexMethod method) {
    return SyntheticItemsTestUtils.isExternalOutlineClass(
            Reference.classFromDescriptor(method.holder.toDescriptorString()))
        && method.name.toString().equals(SyntheticItemsTestUtils.syntheticMethodName());
  }

  @Test
  public void a() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    String returnType = "java.lang.String";
    List<String> parameters = Collections.singletonList("java.lang.StringBuilder");
    MethodSignature signature =
        builder.addStaticMethod(
            returnType,
            DEFAULT_METHOD_NAME,
            parameters,
            2,
            "    move-object         v0, p0",
            "    const-string        v1, \"Test\"",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0 }, " + stringBuilderToStringSignature,
            "    move-result-object  v0",
            "    return-object       v0");

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v1, Ljava/lang/StringBuilder;",
        "    invoke-direct       { v1 }, Ljava/lang/StringBuilder;-><init>()V",
        "    invoke-static       { v1 },"
            + " LTest;->method(Ljava/lang/StringBuilder;)Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void");

    for (int i = 2; i < 6; i++) {
      final int j = i;
      Consumer<InternalOptions> options =
          configureOutlineOptions(
              outline -> {
                outline.threshold = 1;
                outline.minSize = j;
                outline.maxSize = j;
              });

      AndroidApp originalApplication = buildApplication(builder);
      AndroidApp processedApplication = processApplication(originalApplication, options);

      // Return the processed method for inspection.
      DexEncodedMethod method = getMethod(processedApplication, signature);

      DexCode code = method.getCode().asDexCode();
      assertTrue(code.instructions[0] instanceof DexConstString);
      assertTrue(code.instructions[1] instanceof DexInvokeStatic);
      DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[1];
      assertTrue(isOutlineMethodName(invoke.getMethod()));

      // Run code and check result.
      String result = runArt(processedApplication);
      assertEquals("TestTestTestTest", result);
    }
  }

  @Test
  public void b() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    String returnType = "java.lang.String";
    List<String> parameters = Collections.singletonList("java.lang.StringBuilder");
    MethodSignature signature =
        builder.addStaticMethod(
            returnType,
            DEFAULT_METHOD_NAME,
            parameters,
            2,
            "    move-object         v0, p0",
            "    const-string        v1, \"Test1\"",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    const-string        v1, \"Test2\"",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    const-string        v1, \"Test3\"",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    const-string        v1, \"Test4\"",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0 }, " + stringBuilderToStringSignature,
            "    move-result-object  v0",
            "    return-object       v0");

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v1, Ljava/lang/StringBuilder;",
        "    invoke-direct       { v1 }, Ljava/lang/StringBuilder;-><init>()V",
        "    invoke-static       { v1 },"
            + " LTest;->method(Ljava/lang/StringBuilder;)Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void");

    for (int i = 2; i < 6; i++) {
      final int finalI = i;
      Consumer<InternalOptions> options =
          configureOutlineOptions(
              outline -> {
                outline.threshold = 1;
                outline.minSize = finalI;
                outline.maxSize = finalI;
              });

      AndroidApp originalApplication = buildApplication(builder);
      AndroidApp processedApplication = processApplication(originalApplication, options);

      // Return the processed method for inspection.
      DexEncodedMethod method = getMethod(processedApplication, signature);

      DexCode code = method.getCode().asDexCode();

      // Up to 4 const instructions before the invoke of the outline.
      int firstOutlineInvoke = Math.min(i, 4);
      for (int j = 0; j < firstOutlineInvoke; j++) {
        assertTrue(code.instructions[j] instanceof DexConstString);
      }
      assertTrue(code.instructions[firstOutlineInvoke] instanceof DexInvokeStatic);
      DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[firstOutlineInvoke];
      assertTrue(isOutlineMethodName(invoke.getMethod()));

      // Run code and check result.
      String result = runArt(processedApplication);
      assertEquals("Test1Test2Test3Test4", result);
    }
  }

  @Test
  public void c() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    // Method with const instructions after the outline.
    String returnType = "int";
    List<String> parameters = Collections.singletonList("java.lang.StringBuilder");
    MethodSignature signature =
        builder.addStaticMethod(
            returnType,
            DEFAULT_METHOD_NAME,
            parameters,
            2,
            "    move-object         v0, p0",
            "    const-string        v1, \"Test\"",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    const               v0, 0",
            "    const               v1, 1",
            "    add-int             v1, v1, v0",
            "    return              v1");

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v1, Ljava/lang/StringBuilder;",
        "    invoke-direct       { v1 }, Ljava/lang/StringBuilder;-><init>()V",
        "    invoke-static       { v1 }, LTest;->method(Ljava/lang/StringBuilder;)I",
        "    move-result         v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(I)V",
        "    return-void"
    );

    Consumer<InternalOptions> options = configureOutlineOptions(outline -> outline.threshold = 1);
    AndroidApp originalApplication = buildApplication(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);

    // Return the processed method for inspection.
    DexEncodedMethod method = getMethod(processedApplication, signature);

    DexCode code = method.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof DexConstString);
    assertTrue(code.instructions[1] instanceof DexInvokeStatic);
    DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[1];
    assertTrue(isOutlineMethodName(invoke.getMethod()));

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("1", result);
  }

  @Test
  public void d() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    // Method with mixed use of arguments and locals.
    String returnType = "java.lang.String";
    List<String> parameters = ImmutableList.of(
        "java.lang.StringBuilder", "java.lang.String", "java.lang.String");
    MethodSignature signature =
        builder.addStaticMethod(
            returnType,
            DEFAULT_METHOD_NAME,
            parameters,
            2,
            "    invoke-virtual      { p0, p1 }, " + stringBuilderAppendSignature,
            "    move-result-object  p0",
            "    const-string        v0, \"Test1\"",
            "    invoke-virtual      { p0, v0 }, " + stringBuilderAppendSignature,
            "    move-result-object  p0",
            "    invoke-virtual      { p0, p2 }, " + stringBuilderAppendSignature,
            "    move-result-object  p0",
            "    const-string        v1, \"Test2\"",
            "    invoke-virtual      { p0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  p0",
            "    const-string        v1, \"Test3\"",
            "    invoke-virtual      { p0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  p0",
            "    invoke-virtual      { p0 }, " + stringBuilderToStringSignature,
            "    move-result-object  v1",
            "    return-object  v1");

    builder.addMainMethod(
        4,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v1, Ljava/lang/StringBuilder;",
        "    invoke-direct       { v1 }, Ljava/lang/StringBuilder;-><init>()V",
        "    const-string        v2, \"TestX\"",
        "    const-string        v3, \"TestY\"",
        "    invoke-static       { v1, v2, v3 },"
            + " LTest;->method(Ljava/lang/StringBuilder;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void");

    Consumer<InternalOptions> options = configureOutlineOptions(outline -> outline.threshold = 1);
    AndroidApp originalApplication = buildApplication(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);

    // Return the processed method for inspection.
    DexEncodedMethod method = getMethod(processedApplication, signature);

    DexCode code = method.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof DexConstString);
    assertTrue(code.instructions[1] instanceof DexConstString);
    assertTrue(code.instructions[2] instanceof DexInvokeStatic);
    DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[2];
    assertTrue(isOutlineMethodName(invoke.getMethod()));

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("TestXTest1TestYTest2Test3", result);
  }

  @Test
  public void longArguments() throws Throwable {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    String returnType = "java.lang.String";
    List<String> parameters = Collections.singletonList("java.lang.StringBuilder");
    MethodSignature signature =
        builder.addStaticMethod(
            returnType,
            DEFAULT_METHOD_NAME,
            parameters,
            3,
            "    move-object         v0, p0",
            "    const-wide          v1, 0x7fffffff00000000L",
            "    invoke-virtual      { v0, v1, v2 }, " + stringBuilderAppendLongSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1, v2 }, " + stringBuilderAppendLongSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1, v2 }, " + stringBuilderAppendLongSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1, v2 }, " + stringBuilderAppendLongSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0 }, " + stringBuilderToStringSignature,
            "    move-result-object  v0",
            "    return-object       v0");

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v1, Ljava/lang/StringBuilder;",
        "    invoke-direct       { v1 }, Ljava/lang/StringBuilder;-><init>()V",
        "    invoke-static       { v1 },"
            + " LTest;->method(Ljava/lang/StringBuilder;)Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void");

    for (int i = 2; i < 4; i++) {
      final int finalI = i;
      Consumer<InternalOptions> options =
          configureOutlineOptions(
              outline -> {
                outline.threshold = 1;
                outline.minSize = finalI;
                outline.maxSize = finalI;
              });

      AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
      AndroidApp processedApplication = processApplication(originalApplication, options);

      // Return the processed method for inspection.
      DexEncodedMethod method = getMethod(processedApplication, signature);

      DexCode code = method.getCode().asDexCode();
      assertTrue(code.instructions[0] instanceof DexConstWide);
      if (i < 3) {
        assertTrue(code.instructions[1] instanceof DexInvokeStatic);
        DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[1];
        assertTrue(isOutlineMethodName(invoke.getMethod()));
      } else {
        assertTrue(code.instructions[1] instanceof DexInvokeVirtual);
        assertTrue(code.instructions[2] instanceof DexInvokeVirtual);
        assertTrue(code.instructions[3] instanceof DexInvokeStatic);
        DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[3];
        assertTrue(isOutlineMethodName(invoke.getMethod()));
      }

      // Run code and check result.
      String result = runArt(processedApplication);
      StringBuilder resultBuilder = new StringBuilder();
      for (int j = 0; j < 4; j++) {
        resultBuilder.append(0x7fffffff00000000L);
      }
      assertEquals(resultBuilder.toString(), result);
    }
  }

  @Test
  public void doubleArguments() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    String returnType = "java.lang.String";
    List<String> parameters = Collections.singletonList("java.lang.StringBuilder");
    MethodSignature signature =
        builder.addStaticMethod(
            returnType,
            DEFAULT_METHOD_NAME,
            parameters,
            3,
            "    move-object         v0, p0",
            "    const-wide          v1, 0x3ff0000000000000L",
            "    invoke-virtual      { v0, v1, v2 }, " + stringBuilderAppendDoubleSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1, v2 }, " + stringBuilderAppendDoubleSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1, v2 }, " + stringBuilderAppendDoubleSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1, v2 }, " + stringBuilderAppendDoubleSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0 }, " + stringBuilderToStringSignature,
            "    move-result-object  v0",
            "    return-object       v0");

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v1, Ljava/lang/StringBuilder;",
        "    invoke-direct       { v1 }, Ljava/lang/StringBuilder;-><init>()V",
        "    invoke-static       { v1 },"
            + " LTest;->method(Ljava/lang/StringBuilder;)Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void");

    for (int i = 2; i < 4; i++) {
      final int finalI = i;
      Consumer<InternalOptions> options =
          configureOutlineOptions(
              outline -> {
                outline.threshold = 1;
                outline.minSize = finalI;
                outline.maxSize = finalI;
              });

      AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
      AndroidApp processedApplication = processApplication(originalApplication, options);

      // Return the processed method for inspection.
      DexEncodedMethod method = getMethod(processedApplication, signature);

      DexCode code = method.getCode().asDexCode();
      assertTrue(code.instructions[0] instanceof DexConstWideHigh16);
      if (i < 3) {
        assertTrue(code.instructions[1] instanceof DexInvokeStatic);
        DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[1];
        assertTrue(isOutlineMethodName(invoke.getMethod()));
      } else {
        assertTrue(code.instructions[1] instanceof DexInvokeVirtual);
        assertTrue(code.instructions[2] instanceof DexInvokeVirtual);
        assertTrue(code.instructions[3] instanceof DexInvokeStatic);
        DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[3];
        assertTrue(isOutlineMethodName(invoke.getMethod()));
      }

      // Run code and check result.
      String result = runArt(processedApplication);
      StringBuilder resultBuilder = new StringBuilder();
      for (int j = 0; j < 4; j++) {
        resultBuilder.append(1.0d);
      }
      assertEquals(resultBuilder.toString(), result);
    }
  }

  @Test
  public void invokeStatic() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    String returnType = "void";
    List<String> parameters = ImmutableList.of("java.lang.StringBuilder", "int");
    builder.addStaticMethod(
        returnType,
        DEFAULT_METHOD_NAME,
        parameters,
        1,
        "    invoke-virtual      { p0, p1 }, " + stringBuilderAppendIntSignature,
        "    move-result-object  v0",
        "    invoke-virtual      { p0, p1 }, " + stringBuilderAppendIntSignature,
        "    move-result-object  v0",
        "    return-void");

    MethodSignature mainSignature =
        builder.addMainMethod(
            2,
            "    new-instance        v0, Ljava/lang/StringBuilder;",
            "    invoke-direct       { v0 }, Ljava/lang/StringBuilder;-><init>()V",
            "    const/4             v1, 0x1",
            "    invoke-static       { v0, v1 }, LTest;->method(Ljava/lang/StringBuilder;I)V",
            "    const/4             v1, 0x2",
            "    invoke-static       { v0, v1 }, LTest;->method(Ljava/lang/StringBuilder;I)V",
            "    invoke-virtual      { v0 }, " + stringBuilderToStringSignature,
            "    move-result-object  v1",
            "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
            "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
            "    return-void");

    for (int i = 2; i < 6; i++) {
      final int finalI = i;
      Consumer<InternalOptions> options =
          configureOutlineOptions(
              outline -> {
                outline.threshold = 1;
                outline.minSize = finalI;
                outline.maxSize = finalI;
              });

      AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
      AndroidApp processedApplication = processApplication(originalApplication, options);

      // Return the processed main method for inspection.
      DexEncodedMethod mainMethod = getMethod(processedApplication, mainSignature);
      DexCode mainCode = mainMethod.getCode().asDexCode();

      if (i == 2 || i == 3) {
        assert mainCode.instructions.length == 10;
      } else if (i == 4) {
        assert mainCode.instructions.length == 9;
      } else {
        assert i == 5;
        assert mainCode.instructions.length == 7;
      }
      if (i == 2) {
        DexInvokeStatic invoke = (DexInvokeStatic) mainCode.instructions[4];
        assertTrue(isOutlineMethodName(invoke.getMethod()));
      } else if (i == 3) {
        DexInvokeStatic invoke = (DexInvokeStatic) mainCode.instructions[1];
        assertTrue(isOutlineMethodName(invoke.getMethod()));
      } else {
        assert i == 4 || i == 5;
        DexInvokeStatic invoke = (DexInvokeStatic) mainCode.instructions[2];
        assertTrue(isOutlineMethodName(invoke.getMethod()));
      }

      // Run code and check result.
      String result = runArt(processedApplication);
      assertEquals("1122", result);
    }
  }

  @Test
  public void constructor() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    MethodSignature signature1 =
        builder.addStaticMethod(
            "java.lang.String",
            "method1",
            Collections.emptyList(),
            3,
            "    new-instance        v0, Ljava/lang/StringBuilder;",
            "    invoke-direct       { v0 }, Ljava/lang/StringBuilder;-><init>()V",
            "    const-string        v1, \"Test1\"",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0 }, " + stringBuilderToStringSignature,
            "    move-result-object  v0",
            "    return-object       v0");

    MethodSignature signature2 =
        builder.addStaticMethod(
            "java.lang.String",
            "method2",
            Collections.emptyList(),
            3,
            "    const/4             v1, 7",
            "    new-instance        v0, Ljava/lang/StringBuilder;",
            "    invoke-direct       { v0, v1 }, Ljava/lang/StringBuilder;-><init>(I)V",
            "    const-string        v1, \"Test2\"",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
            "    move-result-object  v0",
            "    invoke-virtual      { v0 }, " + stringBuilderToStringSignature,
            "    move-result-object  v0",
            "    return-object       v0");

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    invoke-static       {}, LTest;->method1()Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    invoke-static       {}, LTest;->method2()Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void");

    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 1;
              outline.minSize = 7;
              outline.maxSize = 7;
            });

    AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(3, getNumberOfProgramClasses(processedApplication));

    DexCode code1 = getMethod(processedApplication, signature1).getCode().asDexCode();
    assertEquals(4, code1.instructions.length);
    assertTrue(code1.instructions[1] instanceof DexInvokeStatic);
    DexInvokeStatic invoke1 = (DexInvokeStatic) code1.instructions[1];
    assertTrue(isOutlineMethodName(invoke1.getMethod()));

    DexCode code2 = getMethod(processedApplication, signature2).getCode().asDexCode();
    assertEquals(5, code2.instructions.length);
    assertTrue(code2.instructions[2] instanceof DexInvokeStatic);
    DexInvokeStatic invoke2 = (DexInvokeStatic) code2.instructions[2];
    assertTrue(isOutlineMethodName(invoke1.getMethod()));

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("Test1Test1Test1Test1Test2Test2Test2Test2", result);
  }

  @Test
  public void constructorDontSplitNewInstanceAndInit() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    MethodSignature signature =
        builder.addStaticMethod(
            "java.lang.String",
            DEFAULT_METHOD_NAME,
            ImmutableList.of("java.lang.StringBuilder"),
            2,
            "    const-string        v0, \"Test1\"",
            "    invoke-virtual      { p0, v0 }, " + stringBuilderAppendSignature,
            "    move-result-object  p0",
            "    invoke-virtual      { p0, v0 }, " + stringBuilderAppendSignature,
            "    move-result-object  p0",
            "    new-instance        v1, Ljava/lang/StringBuilder;",
            "    invoke-direct       { v1 }, Ljava/lang/StringBuilder;-><init>()V",
            "    const-string        v0, \"Test2\"",
            "    invoke-virtual      { v1, v0 }, " + stringBuilderAppendSignature,
            "    move-result-object  v1",
            "    invoke-virtual      { v1, v0 }, " + stringBuilderAppendSignature,
            "    move-result-object  v1",
            "    invoke-virtual      { v1 }, " + stringBuilderToStringSignature,
            "    move-result-object  v0",
            "    return-object       v0");

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v1, Ljava/lang/StringBuilder;",
        "    invoke-direct       { v1 }, Ljava/lang/StringBuilder;-><init>()V",
        "    invoke-static       { v1 },"
            + " LTest;->method(Ljava/lang/StringBuilder;)Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void");

    for (int i = 2; i < 8; i++) {
      final int finalI = i;
      Consumer<InternalOptions> options =
          configureOutlineOptions(
              outline -> {
                outline.threshold = 1;
                outline.minSize = finalI;
                outline.maxSize = finalI;
              });

      AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
      AndroidApp processedApplication = processApplication(originalApplication, options);

      DexCode code = getMethod(processedApplication, signature).getCode().asDexCode();
      int outlineInstructionIndex;
      switch (i) {
        case 2:
        case 4:
          outlineInstructionIndex = 1;
          break;
        case 3:
          outlineInstructionIndex = 4;
          break;
        default:
          outlineInstructionIndex = 2;
      }
      DexInstruction instruction = code.instructions[outlineInstructionIndex];
      if (instruction instanceof DexInvokeStatic) {
        DexInvokeStatic invoke = (DexInvokeStatic) instruction;
        assertTrue(isOutlineMethodName(invoke.getMethod()));
      } else {
        DexInvokeStaticRange invoke = (DexInvokeStaticRange) instruction;
        assertTrue(isOutlineMethodName(invoke.getMethod()));
      }

      // Run code and check result.
      String result = runArt(processedApplication);
      assertEquals("Test2Test2", result);
    }
  }

  @Test
  public void outlineWithoutArguments() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    MethodSignature signature1 =
        builder.addStaticMethod(
            "java.lang.String",
            DEFAULT_METHOD_NAME,
            Collections.emptyList(),
            1,
            "    new-instance        v0, Ljava/lang/StringBuilder;",
            "    invoke-direct       { v0 }, Ljava/lang/StringBuilder;-><init>()V",
            "    invoke-virtual      { v0 }, " + stringBuilderToStringSignature,
            "    move-result-object  v0",
            "    return-object       v0");

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    invoke-static       {}, LTest;->method()Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void");

    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 1;
              outline.minSize = 3;
              outline.maxSize = 3;
            });

    AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(2, getNumberOfProgramClasses(processedApplication));

    DexCode code = getMethod(processedApplication, signature1).getCode().asDexCode();
    DexInvokeStatic invoke;
    assertTrue(code.instructions[0] instanceof DexInvokeStatic);
    invoke = (DexInvokeStatic) code.instructions[0];
    assertTrue(isOutlineMethodName(invoke.getMethod()));

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("", result);
  }

  @Test
  public void outlineDifferentReturnType() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    List<String> parameters = Collections.singletonList("java.lang.StringBuilder");

    // The naming of the methods in this test is important. The method name that don't use the
    // output from StringBuilder.toString must sort before the method name that does.
    String returnType1 = "void";
    builder.addStaticMethod(
        returnType1,
        "method1",
        parameters,
        2,
        "    move-object         v0, p0",
        "    const-string        v1, \"Test\"",
        "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
        "    move-result-object  v0",
        "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
        "    move-result-object  v0",
        "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
        "    return-void");

    String returnType2 = "java.lang.String";
    builder.addStaticMethod(
        returnType2,
        "method2",
        parameters,
        2,
        "    move-object         v0, p0",
        "    const-string        v1, \"Test\"",
        "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
        "    move-result-object  v0",
        "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
        "    move-result-object  v0",
        "    invoke-virtual      { v0 }, " + stringBuilderToStringSignature,
        "    move-result-object  v0",
        "    return-object       v0");

    builder.addMainMethod(
        3,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v1, Ljava/lang/StringBuilder;",
        "    invoke-direct       { v1 }, Ljava/lang/StringBuilder;-><init>()V",
        "    invoke-static       { v1 }, LTest;->method1(Ljava/lang/StringBuilder;)V",
        "    invoke-static       { v1 },"
            + " LTest;->method2(Ljava/lang/StringBuilder;)Ljava/lang/String;",
        "    move-result-object  v2",
        "    invoke-virtual      { v0, v2 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void");

    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 1;
              outline.minSize = 3;
              outline.maxSize = 3;
            });

    AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(4, getNumberOfProgramClasses(processedApplication));

    // Check that three outlining methods was created.
    CodeInspector inspector = new CodeInspector(processedApplication);
    List<DexEncodedMethod> outlineMethods = getOutlineMethods(inspector);
    assertEquals(3, outlineMethods.size());
    // Collect the return types of the outlines for the body of method1 and method2.
    List<DexType> r = new ArrayList<>();
    for (DexEncodedMethod directMethod : outlineMethods) {
      if (directMethod.getCode().asDexCode().instructions[0] instanceof DexInvokeVirtual) {
        r.add(directMethod.getReference().proto.returnType);
      }
    }
    assertEquals(2, r.size());
    DexType r1 = r.get(0);
    DexType r2 = r.get(1);
    DexItemFactory factory = inspector.getFactory();
    assertTrue(r1 == factory.voidType && r2 == factory.stringType ||
        r1 == factory.stringType && r2 == factory.voidType);

    // Run the code.
    String result = runArt(processedApplication);
    assertEquals("TestTestTestTestTest", result);
  }

  private static List<DexEncodedMethod> getOutlineMethods(CodeInspector inspector) {
    List<DexEncodedMethod> outlineMethods = new ArrayList<>();
    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (SyntheticItemsTestUtils.isExternalOutlineClass(clazz.getFinalReference())) {
        clazz.forAllMethods(m -> outlineMethods.add(m.getMethod()));
      }
    }
    return outlineMethods;
  }

  @Test
  public void outlineMultipleTimes() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    String returnType = "java.lang.String";
    List<String> parameters = Collections.singletonList("java.lang.StringBuilder");
    builder.addStaticMethod(
        returnType,
        DEFAULT_METHOD_NAME,
        parameters,
        2,
        "    move-object         v0, p0",
        "    const-string        v1, \"Test\"",
        "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
        "    move-result-object  v0",
        "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
        "    move-result-object  v0",
        "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
        "    move-result-object  v0",
        "    invoke-virtual      { v0, v1 }, " + stringBuilderAppendSignature,
        "    move-result-object  v0",
        "    invoke-virtual      { v0 }, " + stringBuilderToStringSignature,
        "    move-result-object  v0",
        "    return-object       v0");

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v1, Ljava/lang/StringBuilder;",
        "    invoke-direct       { v1 }, Ljava/lang/StringBuilder;-><init>()V",
        "    invoke-static       { v1 },"
            + " LTest;->method(Ljava/lang/StringBuilder;)Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void");

    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 1;
              outline.minSize = 3;
              outline.maxSize = 3;
            });

    AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(3, getNumberOfProgramClasses(processedApplication));

    final int count = 10;
    // Process the application several times. Each time will outline the previous outlines.
    for (int i = 1; i < count; i++) {
      // Build a new application with the Outliner class.
      originalApplication = processedApplication;
      processedApplication = processApplication(originalApplication, options);
      assertEquals((i + 1) * 3, getNumberOfProgramClasses(processedApplication));
    }

    // Process the application several times. No more outlining as threshold has been raised.
    options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 2;
              outline.minSize = 3;
              outline.maxSize = 3;
            });
    for (int i = 0; i < count; i++) {
      // Build a new application with the Outliner class.
      originalApplication = processedApplication;
      processedApplication = processApplication(originalApplication, options);
      assertEquals(count * 3, getNumberOfProgramClasses(processedApplication));
    }

    // Run the application with several levels of outlining.
    String result = runArt(processedApplication);
    assertEquals("TestTestTestTest", result);
  }

  @Test
  public void outlineReturnLong() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    MethodSignature signature = builder.addStaticMethod(
        "long",
        DEFAULT_METHOD_NAME,
        ImmutableList.of("int"),
        2,
        "    new-instance        v0, Ljava/util/GregorianCalendar;",
        "    invoke-direct       { v0 }, Ljava/util/GregorianCalendar;-><init>()V",
        "    invoke-virtual      { v0, p0, p0 }, Ljava/util/Calendar;->set(II)V",
        "    invoke-virtual      { v0, p0, p0 }, Ljava/util/Calendar;->set(II)V",
        "    invoke-virtual      { v0 }, Ljava/util/Calendar;->getTimeInMillis()J",
        "    move-result-wide    v0",
        "    return-wide         v0"
    );

    builder.addMainMethod(
        3,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const/4             v1, 0",
        "    invoke-static       { v1 }, LTest;->method(I)J",
        "    move-result-wide    v1",
        "    invoke-virtual      { v0, v1, v2 }, Ljava/io/PrintStream;->print(J)V",
        "    return-void"
    );

    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 1;
              outline.minSize = 5;
              outline.maxSize = 5;
            });

    AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(2, getNumberOfProgramClasses(processedApplication));

    // Return the processed method for inspection.
    DexEncodedMethod method = getMethod(processedApplication, signature);
    // The calls to set, set and getTimeInMillis was outlined.
    DexCode code = method.getCode().asDexCode();
    assertEquals(3, code.instructions.length);
    assertTrue(code.instructions[0] instanceof DexInvokeStatic);
    assertTrue(code.instructions[1] instanceof DexMoveResultWide);
    assertTrue(code.instructions[2] instanceof DexReturnWide);
    DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[0];
    assertEquals(firstOutlineMethodName(), invoke.getMethod().qualifiedName());

    // Run the code and expect a parsable long.
    String result = runArt(processedApplication);
    Long.parseLong(result);
  }

  @Test
  public void noOutlineSuperCalls() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Super");
    builder.addDefaultConstructor();
    builder.addInstanceMethod("void", "set", ImmutableList.of("int", "int"), 0,
        "return-void");

    builder.addInstanceMethod("java.lang.String", "toString", Collections.emptyList(), 1,
        "const-string     v0, \"Hello\"",
        "return-object    v0");

    builder.addClass(DEFAULT_CLASS_NAME, "Super");
    builder.addDefaultConstructor();

    builder.addStaticMethod(
        "java.lang.String",
        DEFAULT_METHOD_NAME,
        ImmutableList.of("int"),
        2,
        "    new-instance        v0, LTest;",
        "    invoke-direct       { v0 }, LTest;-><init>()V",
        "    invoke-super        { v0, p0, p0 }, LTest;->set(II)V",
        "    invoke-super        { v0, p0, p0 }, LTest;->set(II)V",
        "    invoke-virtual      { v0 }, LTest;->toString()Ljava/lang/String;",
        "    move-result-object  v0",
        "    return-object       v0"
    );

    builder.addMainMethod(
        3,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const/4             v1, 0",
        "    invoke-static       { v1 }, LTest;->method(I)Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void"
    );

    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 1;
              outline.minSize = 5;
              outline.maxSize = 5;
            });

    AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(2, getNumberOfProgramClasses(processedApplication));

    String originalResult = runArt(originalApplication);
    String processedResult = runArt(processedApplication);
    Assert.assertEquals(originalResult, processedResult);
  }

  @Test
  public void outlineArrayType() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    builder.addStaticMethod(
        "void",
        "addToList",
        ImmutableList.of("java.util.List", "int[]"),
        0,
        "    invoke-interface    { p0, p1 }, Ljava/util/List;->add(Ljava/lang/Object;)Z",
        "    return-void "
    );

    MethodSignature signature = builder.addStaticMethod(
        "int[]",
        DEFAULT_METHOD_NAME,
        ImmutableList.of("int[]", "int[]"),
        1,
        "    new-instance        v0, Ljava/util/ArrayList;",
        "    invoke-direct       { v0 }, Ljava/util/ArrayList;-><init>()V",
        "    invoke-static       { v0, p0 }, LTest;->addToList(Ljava/util/List;[I)V",
        "    invoke-static       { v0, p1 }, LTest;->addToList(Ljava/util/List;[I)V",
        "    return-object       p0"
    );

    builder.addMainMethod(
        3,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const/4             v1, 0",
        "    invoke-static       { v1, v1 }, LTest;->method([I[I)[I",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/Object;)V",
        "    return-void"
    );

    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 1;
              outline.minSize = 4;
              outline.maxSize = 4;
            });

    AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(2, getNumberOfProgramClasses(processedApplication));

    // Return the processed method for inspection.
    DexEncodedMethod method = getMethod(processedApplication, signature);
    DexCode code = method.getCode().asDexCode();
    assertEquals(2, code.instructions.length);
    assertTrue(code.instructions[0] instanceof DexInvokeStatic);
    assertTrue(code.instructions[1] instanceof DexReturnObject);
    DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[0];
    assertEquals(firstOutlineMethodName(), invoke.getMethod().qualifiedName());

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("null", result);
  }

  @Test
  public void outlineArithmeticBinop() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    builder.addStaticMethod(
        "void",
        "addToList",
        ImmutableList.of("java.util.List", "int[]"),
        0,
        "    invoke-interface    { p0, p1 }, Ljava/util/List;->add(Ljava/lang/Object;)Z",
        "    return-void "
    );

    MethodSignature signature1 = builder.addStaticMethod(
        "int",
        "method1",
        ImmutableList.of("int", "int"),
        1,
        "    add-int             v0, v1, v2",
        "    sub-int             v0, v0, v1",
        "    mul-int             v0, v2, v0",
        "    div-int             v0, v0, v1",
        "    return              v0"
    );

    MethodSignature signature2 = builder.addStaticMethod(
        "int",
        "method2",
        ImmutableList.of("int", "int"),
        1,
        "    add-int             v0, v2, v1",
        "    sub-int             v0, v0, v1",
        "    mul-int             v0, v0, v2",
        "    div-int             v0, v0, v1",
        "    return              v0"
    );

    builder.addMainMethod(
        4,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const/4             v1, 1",
        "    const/4             v2, 2",
        "    invoke-static       { v1, v2 }, LTest;->method1(II)I",
        "    move-result         v4",
        "    invoke-virtual      { v0, v4 }, Ljava/io/PrintStream;->print(I)V",
        "    invoke-static       { v1, v2 }, LTest;->method2(II)I",
        "    move-result         v4",
        "    invoke-virtual      { v0, v4 }, Ljava/io/PrintStream;->print(I)V",
        "    return-void"
    );

    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 1;
              outline.minSize = 4;
              outline.maxSize = 4;
            });

    AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);

    // Return the processed method for inspection.
    DexEncodedMethod method1 = getMethod(processedApplication, signature1);
    DexCode code1 = method1.getCode().asDexCode();
    assertEquals(3, code1.instructions.length);
    assertTrue(code1.instructions[0] instanceof DexInvokeStatic);
    assertTrue(code1.instructions[1] instanceof DexMoveResult);
    assertTrue(code1.instructions[2] instanceof DexReturn);
    DexInvokeStatic invoke1 = (DexInvokeStatic) code1.instructions[0];
    assertTrue(isOutlineMethodName(invoke1.getMethod()));

    DexEncodedMethod method2 = getMethod(processedApplication, signature2);
    DexCode code2 = method2.getCode().asDexCode();
    assertTrue(code2.instructions[0] instanceof DexInvokeStatic);
    DexInvokeStatic invoke2 = (DexInvokeStatic) code2.instructions[0];
    assertEquals(invoke1.getMethod().qualifiedName(), invoke2.getMethod().qualifiedName());

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("44", result);
  }

  @Test
  public void outlineWithHandler() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    builder.addStaticMethod(
        "void",
        "addToList",
        ImmutableList.of("java.util.List", "int[]"),
        0,
        "    invoke-interface    { p0, p1 }, Ljava/util/List;->add(Ljava/lang/Object;)Z",
        "    return-void "
    );

    MethodSignature signature = builder.addStaticMethod(
        "int",
        "method",
        ImmutableList.of("int", "int"),
        1,
        "    :try_start",
        // Throwing instruction to ensure the handler range does not get collapsed.
        "    div-int             v0, v1, v2",
        "    add-int             v0, v1, v2",
        "    sub-int             v0, v0, v1",
        "    mul-int             v0, v2, v0",
        "    div-int             v0, v0, v1",
        "    :try_end",
        "    :return",
        "    return              v0",
        "    .catch Ljava/lang/ArithmeticException; {:try_start .. :try_end} :catch",
        "    :catch",
        "    const/4             v0, -1",
        "    goto :return"
    );

    builder.addMainMethod(
        4,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const/4             v1, 1",
        "    const/4             v2, 2",
        "    invoke-static       { v1, v2 }, LTest;->method(II)I",
        "    move-result         v4",
        "    invoke-virtual      { v0, v4 }, Ljava/io/PrintStream;->print(I)V",
        "    return-void"
    );

    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 1;
              outline.minSize = 3; // Outline add, sub and mul.
              outline.maxSize = 3;
            });

    AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(2, getNumberOfProgramClasses(processedApplication));

    // Return the processed method for inspection.
    DexEncodedMethod method = getMethod(processedApplication, signature);
    DexCode code = method.getCode().asDexCode();
    assertEquals(7, code.instructions.length);
    assertTrue(code.instructions[0] instanceof DexDivInt);
    assertTrue(code.instructions[1] instanceof DexInvokeStatic);
    assertTrue(code.instructions[2] instanceof DexMoveResult);
    assertTrue(code.instructions[3] instanceof DexDivInt2Addr);
    assertTrue(code.instructions[4] instanceof DexGoto);
    assertTrue(code.instructions[5] instanceof DexConst4);
    assertTrue(code.instructions[6] instanceof DexReturn);
    DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[1];
    assertTrue(isOutlineMethodName(invoke.getMethod()));

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("4", result);
  }

  @Test
  public void outlineUnusedOutValue() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    // The result from neither the div-int is never used.
    MethodSignature signature =
        builder.addStaticMethod(
            "java.lang.Object",
            DEFAULT_METHOD_NAME,
            ImmutableList.of("int", "int"),
            1,
            "    div-int             v0, p0, p1",
            "    new-instance        v0, Ljava/lang/StringBuilder;",
            "    invoke-direct       { v0 }, Ljava/lang/StringBuilder;-><init>()V",
            "    return-object       v0");

    builder.addMainMethod(
        2,
        "    const               v0, 1",
        "    const               v1, 2",
        "    invoke-static       { v0, v1 }, LTest;->method(II)Ljava/lang/Object;",
        "    return-void");

    Consumer<InternalOptions> options =
        configureOptions(
            opts -> {
              opts.outline.threshold = 1;
              opts.outline.minSize = 3;
              opts.outline.maxSize = 3;
            });

    AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(2, getNumberOfProgramClasses(processedApplication));

    // Return the processed method for inspection.
    DexEncodedMethod method = getMethod(processedApplication, signature);
    DexCode code = method.getCode().asDexCode();
    assertEquals(3, code.instructions.length);
    assertTrue(code.instructions[0] instanceof DexInvokeStatic);
    assertTrue(code.instructions[1] instanceof DexMoveResultObject);
    assertTrue(code.instructions[2] instanceof DexReturnObject);
    DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[0];
    assertEquals(firstOutlineMethodName(), invoke.getMethod().qualifiedName());

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("", result);
  }

  @Test
  public void outlineUnusedNewInstanceOutValue() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    // The result from the new-instance instructions are never used (<init> is not even called).
    MethodSignature signature = builder.addStaticMethod(
        "void",
        DEFAULT_METHOD_NAME,
        ImmutableList.of(),
        1,
        "    new-instance        v0, Ljava/lang/StringBuilder;",
        "    new-instance        v0, Ljava/lang/StringBuilder;",
        "    new-instance        v0, Ljava/lang/StringBuilder;",
        "    return-void"
    );

    builder.addMainMethod(
        0,
        "    invoke-static       { }, LTest;->method()V",
        "    return-void"
    );

    Consumer<InternalOptions> options =
        configureOptions(
            opts -> {
              opts.outline.threshold = 1;
              opts.outline.minSize = 3;
              opts.outline.maxSize = 3;

              // Do not allow dead code elimination of the new-instance instructions.
              opts.apiModelingOptions().disableApiModeling();
              // Do not allow dead code elimination of the new-instance instructions. This can be
              // achieved by not assuming that StringBuilder is present.
              DexItemFactory dexItemFactory = opts.itemFactory;
              opts.itemFactory.libraryTypesAssumedToBePresent =
                  new HashSet<>(dexItemFactory.libraryTypesAssumedToBePresent);
              dexItemFactory.libraryTypesAssumedToBePresent.remove(
                  dexItemFactory.stringBuilderType);
            });

    AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(2, getNumberOfProgramClasses(processedApplication));

    // Return the processed method for inspection.
    DexEncodedMethod method = getMethod(processedApplication, signature);
    DexCode code = method.getCode().asDexCode();
    assertEquals(2, code.instructions.length);
    assertTrue(code.instructions[0] instanceof DexInvokeStatic);
    assertTrue(code.instructions[1] instanceof DexReturnVoid);
    DexInvokeStatic invoke = (DexInvokeStatic) code.instructions[0];
    assertEquals(firstOutlineMethodName(), invoke.getMethod().qualifiedName());

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("", result);
  }

  @Test
  public void regress33733666() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    builder.addStaticMethod(
        "void",
        DEFAULT_METHOD_NAME,
        Collections.emptyList(),
        4,
        "    new-instance        v0, Ljava/util/Hashtable;",
        "    invoke-direct       { v0 }, Ljava/util/Hashtable;-><init>()V",
        "    const-string        v1, \"http://schemas.google.com/g/2005#home\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x01  # 1",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \" http://schemas.google.com/g/2005#work\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x02  # 2",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#other\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x03  # 3",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#primary\"",
        "    const/4             v2, 0x04  # 4",
        "    invoke-static       { v2 }, Ljava/lang/Byte;->valueOf(B)Ljava/lang/Byte;",
        "    move-result-object  v2",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    sput-object         v0, LA;->A:Ljava/util/Hashtable;",
        "    invoke-static       { v0 }, LA;->a(Ljava/util/Hashtable;)Ljava/util/Hashtable;",
        "    move-result-object  v0",
        "    sput-object         v0, LA;->B:Ljava/util/Hashtable;",
        "    new-instance        v0, Ljava/util/Hashtable;",
        "    invoke-direct       { v0 }, Ljava/util/Hashtable;-><init>()V",
        "    const-string        v1, \"http://schemas.google.com/g/2005#home\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x02  # 2",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#mobile\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x01  # 1",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#pager\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x06  # 6",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#work\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x03  # 3",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#home_fax\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x05  # 5",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#work_fax\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x04  # 4",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#other\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x07  # 7",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    sput-object         v0, LA;->C:Ljava/util/Hashtable;",
        "    invoke-static       { v0 }, LA;->a(Ljava/util/Hashtable;)Ljava/util/Hashtable;",
        "    move-result-object  v0",
        "    sput-object         v0, LA;->D:Ljava/util/Hashtable;",
        "    new-instance        v0, Ljava/util/Hashtable;",
        "    invoke-direct       { v0 }, Ljava/util/Hashtable;-><init>()V",
        "    const-string        v1, \"http://schemas.google.com/g/2005#home\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x01  # 1",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#work\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x02  # 2",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#other\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x03  # 3",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    sput-object         v0, LA;->E:Ljava/util/Hashtable;",
        "    invoke-static       { v0 }, LA;->a(Ljava/util/Hashtable;)Ljava/util/Hashtable;",
        "    move-result-object  v0",
        "    sput-object         v0, LA;->F:Ljava/util/Hashtable;",
        "    new-instance        v0, Ljava/util/Hashtable;",
        "    invoke-direct       { v0 }, Ljava/util/Hashtable;-><init>()V",
        "    const-string        v1, \"http://schemas.google.com/g/2005#home\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x01  # 1",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#work\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x02  # 2",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#other\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x03  # 3",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    sput-object         v0, LA;->G:Ljava/util/Hashtable;",
        "    invoke-static       { v0 }, LA;->a(Ljava/util/Hashtable;)Ljava/util/Hashtable;",
        "    move-result-object  v0",
        "    sput-object         v0, LA;->H:Ljava/util/Hashtable;",
        "    new-instance        v0, Ljava/util/Hashtable;",
        "    invoke-direct       { v0 }, Ljava/util/Hashtable;-><init>()V",
        "    const-string        v1, \"http://schemas.google.com/g/2005#work\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x01  # 1",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#other\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x02  # 2",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    sput-object         v0, LA;->I:Ljava/util/Hashtable;",
        "    invoke-static       { v0 }, LA;->a(Ljava/util/Hashtable;)Ljava/util/Hashtable;",
        "    move-result-object  v0",
        "    sput-object         v0, LA;->J:Ljava/util/Hashtable;",
        "    new-instance        v0, Ljava/util/Hashtable;",
        "    invoke-direct       { v0 }, Ljava/util/Hashtable;-><init>()V",
        "    const-string        v1, \"http://schemas.google.com/g/2005#AIM\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x02  # 2",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#MSN\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x03  # 3",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#YAHOO\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x04  # 4",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#SKYPE\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x05  # 5",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#QQ\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x06  # 6",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#GOOGLE_TALK\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/4             v3, 0x07  # 7",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#ICQ\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/16            v3, 0x0008  # 8",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    const-string        v1, \"http://schemas.google.com/g/2005#JABBER\"",
        "    new-instance        v2, Ljava/lang/Byte;",
        "    const/16            v3, 0x0009  # 9",
        "    invoke-direct       { v2, v3 }, Ljava/lang/Byte;-><init>(B)V",
        "    invoke-virtual      { v0, v1, v2 },"
            + " Ljava/util/Hashtable;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "    sput-object         v0, LA;->K:Ljava/util/Hashtable;",
        "    invoke-static       { v0 }, LA;->a(Ljava/util/Hashtable;)Ljava/util/Hashtable;",
        "    move-result-object  v0",
        "    sput-object         v0, LA;->L:Ljava/util/Hashtable;",
        "    return-void");

    builder.addClass("A");

    Consumer<InternalOptions> options = configureOutlineOptions(outline -> outline.threshold = 2);

    AndroidApp originalApplication = buildApplicationWithAndroidJar(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);

    // Verify the code.
    runDex2Oat(processedApplication);
  }

  private static boolean isOutlineInvoke(DexInstruction instruction) {
    return instruction instanceof DexInvokeStatic && isOutlineMethodName(instruction.getMethod());
  }

  private void assertHasOutlineInvoke(DexEncodedMethod method) {
    assertTrue(
        Arrays
            .stream(method.getCode().asDexCode().instructions)
            .anyMatch(OutlineTest::isOutlineInvoke));
  }

  @Test
  public void b113145696_superClassUseFirst() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    builder.addDefaultConstructor();

    // Code where the outline argument is first used as java.lang.Object and afterwards
    // java.util.ArrayList.
    List<String> codeToOutline = ImmutableList.of(
        "    invoke-virtual      { v1, v2 }, Ljava/io/PrintStream;->print(Ljava/lang/Object;)V",
        "    invoke-virtual      { v2 }, Ljava/util/ArrayList;->isEmpty()Z",
        "    move-result         v0",
        "    return              v0"
    );

    String returnType = "boolean";
    List<String> parameters = ImmutableList.of("java.io.PrintStream", "java.util.ArrayList");
    MethodSignature signature1 = builder.addPrivateInstanceMethod(
        returnType,
        DEFAULT_METHOD_NAME + "1",
        parameters,
        0,
        codeToOutline.toArray(StringUtils.EMPTY_ARRAY)
    );

    MethodSignature signature2 = builder.addPrivateInstanceMethod(
        returnType,
        DEFAULT_METHOD_NAME + "2",
        parameters,
        0,
        codeToOutline.toArray(StringUtils.EMPTY_ARRAY)
    );

    builder.addMainMethod(
        3,
        "    new-instance        v0, LTest;",
        "    invoke-direct       { v0 }, LTest;-><init>()V",
        "    sget-object         v1, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v2, Ljava/util/ArrayList;",
        "    invoke-direct       { v2 }, Ljava/util/ArrayList;-><init>()V",
        "    invoke-direct       { v0, v1, v2 }, "
            + "LTest;->method1(Ljava/io/PrintStream;Ljava/util/ArrayList;)Z",
        "    move-result         v3",
        "    invoke-virtual      { v1, v3 }, Ljava/io/PrintStream;->print(Z)V",
        "    invoke-direct       { v0, v1, v2 }, "
            + "LTest;->method2(Ljava/io/PrintStream;Ljava/util/ArrayList;)Z",
        "    move-result         v3",
        "    invoke-virtual      { v1, v3 }, Ljava/io/PrintStream;->print(Z)V",
        "    return-void");

    // Outline 2 times two instructions.
    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 2;
              outline.minSize = 2;
              outline.maxSize = 2;
            });

    AndroidApp originalApplication = buildApplication(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(2, getNumberOfProgramClasses(processedApplication));

    // Check that outlining happened.
    assertHasOutlineInvoke(getMethod(processedApplication, signature1));
    assertHasOutlineInvoke(getMethod(processedApplication, signature2));
    assertThat(
        new CodeInspector(processedApplication)
            .clazz(OUTLINE_CLASS)
            .method(
                "boolean",
                SyntheticItemsTestUtils.syntheticMethodName(),
                ImmutableList.of("java.io.PrintStream", "java.util.ArrayList")),
        isPresent());

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("[]true[]true", result);
  }

  @Test
  public void b113145696_superInterfaceUseFirst() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    builder.addDefaultConstructor();

    List<String> codeToOutline = ImmutableList.of(
        "    invoke-interface      { v1 }, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;",
        "    invoke-interface      { v1 }, Ljava/util/Collection;->isEmpty()Z",
        "    move-result         v0",
        "    return              v0"
    );

    String returnType = "boolean";
    List<String> parameters = ImmutableList.of("java.util.List");
    MethodSignature signature1 = builder.addPrivateInstanceMethod(
        returnType,
        DEFAULT_METHOD_NAME + "1",
        parameters,
        0,
        codeToOutline.toArray(StringUtils.EMPTY_ARRAY)
    );

    MethodSignature signature2 = builder.addPrivateInstanceMethod(
        returnType,
        DEFAULT_METHOD_NAME + "2",
        parameters,
        0,
        codeToOutline.toArray(StringUtils.EMPTY_ARRAY)
    );

    builder.addMainMethod(
        3,
        "    new-instance        v0, LTest;",
        "    invoke-direct       { v0 }, LTest;-><init>()V",
        "    sget-object         v1, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v2, Ljava/util/ArrayList;",
        "    invoke-direct       { v2 }, Ljava/util/ArrayList;-><init>()V",
        "    invoke-direct       { v0, v2 }, LTest;->method1(Ljava/util/List;)Z",
        "    move-result         v3",
        "    invoke-virtual      { v1, v3 }, Ljava/io/PrintStream;->print(Z)V",
        "    invoke-direct       { v0, v2 }, LTest;->method2(Ljava/util/List;)Z",
        "    move-result         v3",
        "    invoke-virtual      { v1, v3 }, Ljava/io/PrintStream;->print(Z)V",
        "    return-void");

    // Outline 2 times two instructions.
    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 2;
              outline.minSize = 2;
              outline.maxSize = 2;
            });

    AndroidApp originalApplication = buildApplication(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(2, getNumberOfProgramClasses(processedApplication));

    // Check that outlining happened.
    assertHasOutlineInvoke(getMethod(processedApplication, signature1));
    assertHasOutlineInvoke(getMethod(processedApplication, signature2));
    assertThat(
        new CodeInspector(processedApplication)
            .clazz(OUTLINE_CLASS)
            .method(
                "boolean",
                SyntheticItemsTestUtils.syntheticMethodName(),
                ImmutableList.of("java.util.List")),
        isPresent());

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("truetrue", result);
  }

  @Test
  public void b113145696_interfaceUseFirst() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    builder.addDefaultConstructor();

    // Code where the outline argument is first used as java.lang.Iterable and afterwards
    // java.util.ArrayList.
    List<String> codeToOutline = ImmutableList.of(
        "    invoke-interface    { v1 }, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;",
        "    invoke-virtual      { v1 }, Ljava/util/ArrayList;->isEmpty()Z",
        "    move-result         v0",
        "    return              v0"
    );

    String returnType = "boolean";
    List<String> parameters = ImmutableList.of("java.util.ArrayList");
    MethodSignature signature1 = builder.addPrivateInstanceMethod(
        returnType,
        DEFAULT_METHOD_NAME + "1",
        parameters,
        0,
        codeToOutline.toArray(StringUtils.EMPTY_ARRAY)
    );

    MethodSignature signature2 = builder.addPrivateInstanceMethod(
        returnType,
        DEFAULT_METHOD_NAME + "2",
        parameters,
        0,
        codeToOutline.toArray(StringUtils.EMPTY_ARRAY)
    );

    builder.addMainMethod(
        3,
        "    new-instance        v0, LTest;",
        "    invoke-direct       { v0 }, LTest;-><init>()V",
        "    sget-object         v1, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v2, Ljava/util/ArrayList;",
        "    invoke-direct       { v2 }, Ljava/util/ArrayList;-><init>()V",
        "    invoke-direct       { v0, v2 }, LTest;->method1(Ljava/util/ArrayList;)Z",
        "    move-result         v3",
        "    invoke-virtual      { v1, v3 }, Ljava/io/PrintStream;->print(Z)V",
        "    invoke-direct       { v0, v2 }, LTest;->method2(Ljava/util/ArrayList;)Z",
        "    move-result         v3",
        "    invoke-virtual      { v1, v3 }, Ljava/io/PrintStream;->print(Z)V",
        "    return-void");

    // Outline 2 times two instructions.
    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 2;
              outline.minSize = 2;
              outline.maxSize = 2;
            });

    AndroidApp originalApplication = buildApplication(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(2, getNumberOfProgramClasses(processedApplication));

    // Check that outlining happened.
    assertHasOutlineInvoke(getMethod(processedApplication, signature1));
    assertHasOutlineInvoke(getMethod(processedApplication, signature2));
    assertThat(
        new CodeInspector(processedApplication)
            .clazz(OUTLINE_CLASS)
            .method(
                "boolean",
                SyntheticItemsTestUtils.syntheticMethodName(),
                ImmutableList.of("java.util.ArrayList")),
        isPresent());

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("truetrue", result);
  }

  @Test
  public void b113145696_classUseFirst() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    builder.addDefaultConstructor();

    // Code where the outline argument is first used as java.lang.Iterable and afterwards
    // java.util.ArrayList.
    List<String> codeToOutline = ImmutableList.of(
        "    invoke-virtual      { v1 }, Ljava/util/ArrayList;->isEmpty()Z",
        "    move-result         v0",
        "    invoke-interface    { v1 }, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;",
        "    return              v0"
    );

    String returnType = "boolean";
    List<String> parameters = ImmutableList.of("java.util.ArrayList");
    MethodSignature signature1 = builder.addPrivateInstanceMethod(
        returnType,
        DEFAULT_METHOD_NAME + "1",
        parameters,
        0,
        codeToOutline.toArray(StringUtils.EMPTY_ARRAY)
    );

    MethodSignature signature2 = builder.addPrivateInstanceMethod(
        returnType,
        DEFAULT_METHOD_NAME + "2",
        parameters,
        0,
        codeToOutline.toArray(StringUtils.EMPTY_ARRAY)
    );

    builder.addMainMethod(
        3,
        "    new-instance        v0, LTest;",
        "    invoke-direct       { v0 }, LTest;-><init>()V",
        "    sget-object         v1, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v2, Ljava/util/ArrayList;",
        "    invoke-direct       { v2 }, Ljava/util/ArrayList;-><init>()V",
        "    invoke-direct       { v0, v2 }, LTest;->method1(Ljava/util/ArrayList;)Z",
        "    move-result         v3",
        "    invoke-virtual      { v1, v3 }, Ljava/io/PrintStream;->print(Z)V",
        "    invoke-direct       { v0, v2 }, LTest;->method2(Ljava/util/ArrayList;)Z",
        "    move-result         v3",
        "    invoke-virtual      { v1, v3 }, Ljava/io/PrintStream;->print(Z)V",
        "    return-void");

    // Outline 2 times two instructions.
    Consumer<InternalOptions> options =
        configureOutlineOptions(
            outline -> {
              outline.threshold = 2;
              outline.minSize = 2;
              outline.maxSize = 2;
            });

    AndroidApp originalApplication = buildApplication(builder);
    AndroidApp processedApplication = processApplication(originalApplication, options);
    assertEquals(2, getNumberOfProgramClasses(processedApplication));

    // Check that outlining happened.
    assertHasOutlineInvoke(getMethod(processedApplication, signature1));
    assertHasOutlineInvoke(getMethod(processedApplication, signature2));
    assertThat(
        new CodeInspector(processedApplication)
            .clazz(OUTLINE_CLASS)
            .method(
                "boolean",
                SyntheticItemsTestUtils.syntheticMethodName(),
                ImmutableList.of("java.util.ArrayList")),
        isPresent());

    // Run code and check result.
    String result = runArt(processedApplication);
    assertEquals("truetrue", result);
  }
}
