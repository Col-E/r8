// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.constantdynamic;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstantDynamicMultipleConstantsWithDifferentSymbolicReferenceUsingSameBSMAndArgumentsTest
    extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final String MAIN_CLASS = "A";
  private static final String EXPECTED_OUTPUT = StringUtils.lines("false");

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11));
    testForJvm(parameters)
        .addProgramClassFileData(classFileData)
        .disassemble()
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(parameters)
        .addProgramClassFileData(classFileData)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .applyIf(
            // When not desugaring the CF code requires JDK 11.
            DesugarTestConfiguration::isNotDesugared,
            r -> {
              if (parameters.isCfRuntime()
                  && parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11)) {
                r.assertSuccessWithOutput(EXPECTED_OUTPUT);
              } else {
                r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class);
              }
            })
        .applyIf(
            DesugarTestConfiguration::isDesugared, r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(classFileData)
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_CLASS)
        // TODO(b/198142613): There should not be a warnings on class references which are
        //  desugared away.
        .applyIf(
            parameters.getApiLevel().isLessThan(AndroidApiLevel.O),
            b -> b.addDontWarn("java.lang.invoke.MethodHandles$Lookup"))
        // TODO(b/198142625): Support CONSTANT_Dynamic output for class files.
        .applyIf(
            parameters.isCfRuntime(),
            r -> {
              assertThrows(
                  CompilationFailedException.class,
                  () ->
                      r.compileWithExpectedDiagnostics(
                          diagnostics -> {
                            diagnostics.assertOnlyErrors();
                            diagnostics.assertErrorsMatch(
                                diagnosticMessage(
                                    containsString(
                                        "Unsupported dynamic constant (not desugaring)")));
                          }));
            },
            r ->
                r.run(parameters.getRuntime(), MAIN_CLASS)
                    .assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  /*
    This test was supposed to use the following test classes to have two dynamic constants
    using exactly the same bootstrap method and arguments as set up in getTransformedClasses
    below. However, ASM will canonicalize both dynamic constants and bootstrap methods, so instead
    a class file directly from bytes is used.
  */

  private byte[] getTransformedClasses() throws IOException {
      return transformer(A.class)
          .setVersion(CfVersion.V11)
          .transformConstStringToConstantDynamic(
              "condy1", A.class, "myConstant", false, "constantName", Object.class)
          .transformConstStringToConstantDynamic(
              "condy2", A.class, "myConstant", false, "constantName", Object.class)
          .transform();
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(A.getConstant1() == A.getConstant2());
    }
  }

  public static class A {

    public static Object getConstant1() {
      return "condy1"; // Will be transformed to Constant_DYNAMIC.
    }

    public static Object getConstant2() {
      return "condy2"; // Will be transformed to Constant_DYNAMIC.
    }

    private static Object myConstant(MethodHandles.Lookup lookup, String name, Class<?> type) {
      return new Object();
    }
  }

/*

Class file bytes for the following class file:

Classfile A.class
  Last modified Mar 17, 2023; size 1364 bytes
  SHA-256 checksum 4379e359d727521479cee1aa5b6d711090b2777553454d9b667fe502a1b0daa9
  Compiled from "A.java"
public class A
  minor version: 0
  major version: 55
  flags: (0x0021) ACC_PUBLIC, ACC_SUPER
  this_class: #2                          // A
  super_class: #4                         // java/lang/Object
  interfaces: 0, fields: 0, methods: 5, attributes: 3
Constant pool:
   #1 = Utf8               A
   #2 = Class              #1             // A
   #3 = Utf8               java/lang/Object
   #4 = Class              #3             // java/lang/Object
   #5 = Utf8               A.java
   #6 = Utf8               java/lang/invoke/MethodHandles$Lookup
   #7 = Class              #6             // java/lang/invoke/MethodHandles$Lookup
   #8 = Utf8               java/lang/invoke/MethodHandles
   #9 = Class              #8             // java/lang/invoke/MethodHandles
  #10 = Utf8               Lookup
  #11 = Utf8               <init>
  #12 = Utf8               ()V
  #13 = NameAndType        #11:#12        // "<init>":()V
  #14 = Methodref          #4.#13         // java/lang/Object."<init>":()V
  #15 = Utf8               this
  #16 = Utf8               LA;
  #17 = Utf8               getConstant1
  #18 = Utf8               ()Ljava/lang/Object;
  #19 = Utf8               myConstant
  #20 = Utf8               (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;
  #21 = NameAndType        #19:#20        // myConstant:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;
  #22 = Methodref          #2.#21         // A.myConstant:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;
  #23 = MethodHandle       6:#22          // REF_invokeStatic A.myConstant:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;
  #24 = Utf8               constantName
  #25 = Utf8               Ljava/lang/Object;
  #26 = NameAndType        #24:#25        // constantName:Ljava/lang/Object;
  #27 = Dynamic            #0:#26         // #0:constantName:Ljava/lang/Object;
  #28 = Utf8               getConstant2
  #29 = Dynamic            #1:#26         // #1:constantName:Ljava/lang/Object;
  #30 = Utf8               (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class<*>;)Ljava/lang/Object;
  #31 = Utf8               lookup
  #32 = Utf8               Ljava/lang/invoke/MethodHandles$Lookup;
  #33 = Utf8               name
  #34 = Utf8               Ljava/lang/String;
  #35 = Utf8               type
  #36 = Utf8               Ljava/lang/Class<*>;
  #37 = Utf8               Ljava/lang/Class;
  #38 = Utf8               main
  #39 = Utf8               ([Ljava/lang/String;)V
  #40 = Utf8               java/lang/System
  #41 = Class              #40            // java/lang/System
  #42 = Utf8               out
  #43 = Utf8               Ljava/io/PrintStream;
  #44 = NameAndType        #42:#43        // out:Ljava/io/PrintStream;
  #45 = Fieldref           #41.#44        // java/lang/System.out:Ljava/io/PrintStream;
  #46 = NameAndType        #17:#18        // getConstant1:()Ljava/lang/Object;
  #47 = Methodref          #2.#46         // A.getConstant1:()Ljava/lang/Object;
  #48 = NameAndType        #28:#18        // getConstant2:()Ljava/lang/Object;
  #49 = Methodref          #2.#48         // A.getConstant2:()Ljava/lang/Object;
  #50 = Utf8               java/io/PrintStream
  #51 = Class              #50            // java/io/PrintStream
  #52 = Utf8               [Ljava/lang/String;
  #53 = Class              #52            // "[Ljava/lang/String;"
  #54 = Utf8               println
  #55 = Utf8               (Z)V
  #56 = NameAndType        #54:#55        // println:(Z)V
  #57 = Methodref          #51.#56        // java/io/PrintStream.println:(Z)V
  #58 = Utf8               Code
  #59 = Utf8               LineNumberTable
  #60 = Utf8               LocalVariableTable
  #61 = Utf8               LocalVariableTypeTable
  #62 = Utf8               Signature
  #63 = Utf8               StackMapTable
  #64 = Utf8               InnerClasses
  #65 = Utf8               SourceFile
  #66 = Utf8               BootstrapMethods
{
  public A();
    descriptor: ()V
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokespecial #14                 // Method java/lang/Object."<init>":()V
         4: return
      LineNumberTable:
        line 132: 0
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       5     0  this   LA;

  public static java.lang.Object getConstant1();
    descriptor: ()Ljava/lang/Object;
    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
    Code:
      stack=1, locals=0, args_size=0
         0: ldc           #27                 // Dynamic #0:constantName:Ljava/lang/Object;
         2: areturn
      LineNumberTable:
        line 135: 0

  public static java.lang.Object getConstant2();
    descriptor: ()Ljava/lang/Object;
    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
    Code:
      stack=1, locals=0, args_size=0
         0: ldc           #29                 // Dynamic #1:constantName:Ljava/lang/Object;
         2: areturn
      LineNumberTable:
        line 139: 0

  private static java.lang.Object myConstant(java.lang.invoke.MethodHandles$Lookup, java.lang.String, java.lang.Class<?>);
    descriptor: (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;
    flags: (0x000a) ACC_PRIVATE, ACC_STATIC
    Code:
      stack=2, locals=3, args_size=3
         0: new           #4                  // class java/lang/Object
         3: dup
         4: invokespecial #14                 // Method java/lang/Object."<init>":()V
         7: areturn
      LineNumberTable:
        line 143: 0
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       8     0 lookup   Ljava/lang/invoke/MethodHandles$Lookup;
            0       8     1  name   Ljava/lang/String;
            0       8     2  type   Ljava/lang/Class;
      LocalVariableTypeTable:
        Start  Length  Slot  Name   Signature
            0       8     2  type   Ljava/lang/Class<*>;
    Signature: #30                          // (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class<*>;)Ljava/lang/Object;

  public static void main(java.lang.String[]);
    descriptor: ([Ljava/lang/String;)V
    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
    Code:
      stack=3, locals=1, args_size=1
         0: getstatic     #45                 // Field java/lang/System.out:Ljava/io/PrintStream;
         3: invokestatic  #47                 // Method getConstant1:()Ljava/lang/Object;
         6: invokestatic  #49                 // Method getConstant2:()Ljava/lang/Object;
         9: if_acmpne     16
        12: iconst_1
        13: goto          17
        16: iconst_0
        17: invokevirtual #57                 // Method java/io/PrintStream.println:(Z)V
        20: return
      StackMapTable: number_of_entries = 2
        frame_type = 80 // same_locals_1_stack_item
  stack = [ class java/io/PrintStream ]
  frame_type = 255 // full_frame
  offset_delta = 0
  locals = [ class "[Ljava/lang/String;" ]
  stack = [ class java/io/PrintStream, int ]
  LineNumberTable:
  line 12: 0
  line 13: 20
}
InnerClasses:
public static final #10= #7 of #9;      // Lookup=class java/lang/invoke/MethodHandles$Lookup of class java/lang/invoke/MethodHandles
    SourceFile: "A.java"
    BootstrapMethods:
    0: #23 REF_invokeStatic A.myConstant:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;
    Method arguments:
    1: #23 REF_invokeStatic A.myConstant:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;
    Method arguments:
*/

  private byte[] classFileData = {
      -54, -2, -70, -66, 0, 0, 0, 55, 0, 67, 1, 0, 1, 65, 7, 0, 1,
      1, 0, 16, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106,
      101, 99, 116, 7, 0, 3, 1, 0, 6, 65, 46, 106, 97, 118, 97, 1,
      0, 37, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 105, 110, 118, 111,
      107, 101, 47, 77, 101, 116, 104, 111, 100, 72, 97, 110, 100, 108, 101, 115,
      36, 76, 111, 111, 107, 117, 112, 7, 0, 6, 1, 0, 30, 106, 97, 118,
      97, 47, 108, 97, 110, 103, 47, 105, 110, 118, 111, 107, 101, 47, 77, 101,
      116, 104, 111, 100, 72, 97, 110, 100, 108, 101, 115, 7, 0, 8, 1, 0,
      6, 76, 111, 111, 107, 117, 112, 1, 0, 6, 60, 105, 110, 105, 116, 62,
      1, 0, 3, 40, 41, 86, 12, 0, 11, 0, 12, 10, 0, 4, 0, 13,
      1, 0, 4, 116, 104, 105, 115, 1, 0, 3, 76, 65, 59, 1, 0, 12,
      103, 101, 116, 67, 111, 110, 115, 116, 97, 110, 116, 49, 1, 0, 20, 40,
      41, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101,
      99, 116, 59, 1, 0, 10, 109, 121, 67, 111, 110, 115, 116, 97, 110, 116,
      1, 0, 94, 40, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 105,
      110, 118, 111, 107, 101, 47, 77, 101, 116, 104, 111, 100, 72, 97, 110, 100,
      108, 101, 115, 36, 76, 111, 111, 107, 117, 112, 59, 76, 106, 97, 118, 97,
      47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 59, 76, 106, 97,
      118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 59, 41, 76,
      106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116,
      59, 12, 0, 19, 0, 20, 10, 0, 2, 0, 21, 15, 6, 0, 22, 1,
      0, 12, 99, 111, 110, 115, 116, 97, 110, 116, 78, 97, 109, 101, 1, 0,
      18, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101,
      99, 116, 59, 12, 0, 24, 0, 25, 17, 0, 0, 0, 26, 1, 0, 12,
      103, 101, 116, 67, 111, 110, 115, 116, 97, 110, 116, 50, 17, 0, 1, 0,
      26, 1, 0, 97, 40, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47,
      105, 110, 118, 111, 107, 101, 47, 77, 101, 116, 104, 111, 100, 72, 97, 110,
      100, 108, 101, 115, 36, 76, 111, 111, 107, 117, 112, 59, 76, 106, 97, 118,
      97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 59, 76, 106,
      97, 118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 60, 42,
      62, 59, 41, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98,
      106, 101, 99, 116, 59, 1, 0, 6, 108, 111, 111, 107, 117, 112, 1, 0,
      39, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 105, 110, 118, 111,
      107, 101, 47, 77, 101, 116, 104, 111, 100, 72, 97, 110, 100, 108, 101, 115,
      36, 76, 111, 111, 107, 117, 112, 59, 1, 0, 4, 110, 97, 109, 101, 1,
      0, 18, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114,
      105, 110, 103, 59, 1, 0, 4, 116, 121, 112, 101, 1, 0, 20, 76, 106,
      97, 118, 97, 47, 108, 97, 110, 103, 47, 67, 108, 97, 115, 115, 60, 42,
      62, 59, 1, 0, 17, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47,
      67, 108, 97, 115, 115, 59, 1, 0, 4, 109, 97, 105, 110, 1, 0, 22,
      40, 91, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114,
      105, 110, 103, 59, 41, 86, 1, 0, 16, 106, 97, 118, 97, 47, 108, 97,
      110, 103, 47, 83, 121, 115, 116, 101, 109, 7, 0, 40, 1, 0, 3, 111,
      117, 116, 1, 0, 21, 76, 106, 97, 118, 97, 47, 105, 111, 47, 80, 114,
      105, 110, 116, 83, 116, 114, 101, 97, 109, 59, 12, 0, 42, 0, 43, 9,
      0, 41, 0, 44, 12, 0, 17, 0, 18, 10, 0, 2, 0, 46, 12, 0,
      28, 0, 18, 10, 0, 2, 0, 48, 1, 0, 19, 106, 97, 118, 97, 47,
      105, 111, 47, 80, 114, 105, 110, 116, 83, 116, 114, 101, 97, 109, 7, 0,
      50, 1, 0, 19, 91, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47,
      83, 116, 114, 105, 110, 103, 59, 7, 0, 52, 1, 0, 7, 112, 114, 105,
      110, 116, 108, 110, 1, 0, 4, 40, 90, 41, 86, 12, 0, 54, 0, 55,
      10, 0, 51, 0, 56, 1, 0, 4, 67, 111, 100, 101, 1, 0, 15, 76,
      105, 110, 101, 78, 117, 109, 98, 101, 114, 84, 97, 98, 108, 101, 1, 0,
      18, 76, 111, 99, 97, 108, 86, 97, 114, 105, 97, 98, 108, 101, 84, 97,
      98, 108, 101, 1, 0, 22, 76, 111, 99, 97, 108, 86, 97, 114, 105, 97,
      98, 108, 101, 84, 121, 112, 101, 84, 97, 98, 108, 101, 1, 0, 9, 83,
      105, 103, 110, 97, 116, 117, 114, 101, 1, 0, 13, 83, 116, 97, 99, 107,
      77, 97, 112, 84, 97, 98, 108, 101, 1, 0, 12, 73, 110, 110, 101, 114,
      67, 108, 97, 115, 115, 101, 115, 1, 0, 10, 83, 111, 117, 114, 99, 101,
      70, 105, 108, 101, 1, 0, 16, 66, 111, 111, 116, 115, 116, 114, 97, 112,
      77, 101, 116, 104, 111, 100, 115, 0, 33, 0, 2, 0, 4, 0, 0, 0,
      0, 0, 5, 0, 1, 0, 11, 0, 12, 0, 1, 0, 58, 0, 0, 0,
      47, 0, 1, 0, 1, 0, 0, 0, 5, 42, -73, 0, 14, -79, 0, 0,
      0, 2, 0, 59, 0, 0, 0, 6, 0, 1, 0, 0, 0, -124, 0, 60,
      0, 0, 0, 12, 0, 1, 0, 0, 0, 5, 0, 15, 0, 16, 0, 0,
      0, 9, 0, 17, 0, 18, 0, 1, 0, 58, 0, 0, 0, 27, 0, 1,
      0, 0, 0, 0, 0, 3, 18, 27, -80, 0, 0, 0, 1, 0, 59, 0,
      0, 0, 6, 0, 1, 0, 0, 0, -121, 0, 9, 0, 28, 0, 18, 0,
      1, 0, 58, 0, 0, 0, 27, 0, 1, 0, 0, 0, 0, 0, 3, 18,
      29, -80, 0, 0, 0, 1, 0, 59, 0, 0, 0, 6, 0, 1, 0, 0,
      0, -117, 0, 10, 0, 19, 0, 20, 0, 2, 0, 58, 0, 0, 0, 88,
      0, 2, 0, 3, 0, 0, 0, 8, -69, 0, 4, 89, -73, 0, 14, -80,
      0, 0, 0, 3, 0, 59, 0, 0, 0, 6, 0, 1, 0, 0, 0, -113,
      0, 60, 0, 0, 0, 32, 0, 3, 0, 0, 0, 8, 0, 31, 0, 32,
      0, 0, 0, 0, 0, 8, 0, 33, 0, 34, 0, 1, 0, 0, 0, 8,
      0, 35, 0, 37, 0, 2, 0, 61, 0, 0, 0, 12, 0, 1, 0, 0,
      0, 8, 0, 35, 0, 36, 0, 2, 0, 62, 0, 0, 0, 2, 0, 30,
      0, 9, 0, 38, 0, 39, 0, 1, 0, 58, 0, 0, 0, 75, 0, 3,
      0, 1, 0, 0, 0, 21, -78, 0, 45, -72, 0, 47, -72, 0, 49, -90,
      0, 7, 4, -89, 0, 4, 3, -74, 0, 57, -79, 0, 0, 0, 2, 0,
      63, 0, 0, 0, 20, 0, 2, 80, 7, 0, 51, -1, 0, 0, 0, 1,
      7, 0, 53, 0, 2, 7, 0, 51, 1, 0, 59, 0, 0, 0, 10, 0,
      2, 0, 0, 0, 12, 0, 20, 0, 13, 0, 3, 0, 64, 0, 0, 0,
      10, 0, 1, 0, 7, 0, 9, 0, 10, 0, 25, 0, 65, 0, 0, 0,
      2, 0, 5, 0, 66, 0, 0, 0, 10, 0, 2, 0, 23, 0, 0, 0,
      23, 0, 0
  };

  /*

  The class file bytes above was generated from the following ASM visitor code using ASM with the
  patch below applied (was applied on 443339a964352dcec4dd3915de8f13188920d3ac).

  The thing to note is that the two calls

    methodVisitor.visitLdcInsn(new ConstantDynamic(...));

  have the exact same arguments, which ASM will canonicalize making it impossible to
  write the test using standard ASM visitor.

  import java.nio.file.Files;
  import java.nio.file.Paths;

  import org.objectweb.asm.AnnotationVisitor;
  import org.objectweb.asm.Attribute;
  import org.objectweb.asm.ClassReader;
  import org.objectweb.asm.ClassWriter;
  import org.objectweb.asm.ConstantDynamic;
  import org.objectweb.asm.FieldVisitor;
  import org.objectweb.asm.Handle;
  import org.objectweb.asm.Label;
  import org.objectweb.asm.MethodVisitor;
  import org.objectweb.asm.Opcodes;
  import org.objectweb.asm.RecordComponentVisitor;
  import org.objectweb.asm.Type;
  import org.objectweb.asm.TypePath;

  public class DC implements Opcodes {

    public static byte[] dump () throws Exception {

      ClassWriter classWriter = new ClassWriter(0);
      FieldVisitor fieldVisitor;
      RecordComponentVisitor recordComponentVisitor;
      MethodVisitor methodVisitor;
      AnnotationVisitor annotationVisitor0;

      classWriter.visit(V11, ACC_PUBLIC | ACC_SUPER, "A", null, "java/lang/Object", null);

      classWriter.visitSource("A.java", null);

      classWriter.visitInnerClass("java/lang/invoke/MethodHandles$Lookup", "java/lang/invoke/MethodHandles", "Lookup", ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(132, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable("this", "LA;", null, label0, label1, 0);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "getConstant1", "()Ljava/lang/Object;", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(135, label0);
        methodVisitor.visitLdcInsn(new ConstantDynamic("constantName", "Ljava/lang/Object;", new Handle(Opcodes.H_INVOKESTATIC, "A", "myConstant", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;", false), new Object[] {}));
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitMaxs(1, 0);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "getConstant2", "()Ljava/lang/Object;", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(139, label0);
        methodVisitor.visitLdcInsn(new ConstantDynamic("constantName", "Ljava/lang/Object;", new Handle(Opcodes.H_INVOKESTATIC, "A", "myConstant", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;", false), new Object[] {}));
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitMaxs(1, 0);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PRIVATE | ACC_STATIC, "myConstant", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class<*>;)Ljava/lang/Object;", null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(143, label0);
        methodVisitor.visitTypeInsn(NEW, "java/lang/Object");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(ARETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable("lookup", "Ljava/lang/invoke/MethodHandles$Lookup;", null, label0, label1, 0);
        methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label1, 1);
        methodVisitor.visitLocalVariable("type", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", label0, label1, 2);
        methodVisitor.visitMaxs(2, 3);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(12, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitMethodInsn(INVOKESTATIC, "A", "getConstant1", "()Ljava/lang/Object;", false);
        methodVisitor.visitMethodInsn(INVOKESTATIC, "A", "getConstant2", "()Ljava/lang/Object;", false);
        Label label1 = new Label();
        methodVisitor.visitJumpInsn(IF_ACMPNE, label1);
        methodVisitor.visitInsn(ICONST_1);
        Label label2 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label2);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/io/PrintStream"});
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {"[Ljava/lang/String;"}, 2, new Object[] {"java/io/PrintStream", Opcodes.INTEGER});
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Z)V", false);
        Label label3 = new Label();
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(13, label3);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(3, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }

    public static void main(String[] args) throws Exception {
      Files.write(Paths.get("A.class"), DC.dump());
    }
  }

  diff --git a/asm/src/main/java/org/objectweb/asm/SymbolTable.java b/asm/src/main/java/org/objectweb/asm/SymbolTable.java
index a2f26f18..999620c5 100644
--- a/asm/src/main/java/org/objectweb/asm/SymbolTable.java
+++ b/asm/src/main/java/org/objectweb/asm/SymbolTable.java
@@ -922,17 +922,6 @@ final class SymbolTable {
   private Symbol addConstantDynamicOrInvokeDynamicReference(
       final int tag, final String name, final String descriptor, final int bootstrapMethodIndex) {
     int hashCode = hash(tag, name, descriptor, bootstrapMethodIndex);
-    Entry entry = get(hashCode);
-    while (entry != null) {
-      if (entry.tag == tag
-          && entry.hashCode == hashCode
-          && entry.data == bootstrapMethodIndex
-          && entry.name.equals(name)
-          && entry.value.equals(descriptor)) {
-        return entry;
-      }
-      entry = entry.next;
-    }
     constantPool.put122(tag, bootstrapMethodIndex, addConstantNameAndType(name, descriptor));
     return put(
         new Entry(
@@ -1094,24 +1083,6 @@ final class SymbolTable {
  private Symbol addBootstrapMethod(final int offset, final int length, final int hashCode) {
    final byte[] bootstrapMethodsData = bootstrapMethods.data;
-    Entry entry = get(hashCode);
-    while (entry != null) {
-      if (entry.tag == Symbol.BOOTSTRAP_METHOD_TAG && entry.hashCode == hashCode) {
-        int otherOffset = (int) entry.data;
-        boolean isSameBootstrapMethod = true;
-        for (int i = 0; i < length; ++i) {
-          if (bootstrapMethodsData[offset + i] != bootstrapMethodsData[otherOffset + i]) {
-            isSameBootstrapMethod = false;
-            break;
-          }
-        }
-        if (isSameBootstrapMethod) {
-          bootstrapMethods.length = offset; // Revert to old position.
-          return entry;
-        }
-      }
-      entry = entry.next;
-    }
    return put(new Entry(bootstrapMethodCount++, Symbol.BOOTSTRAP_METHOD_TAG, offset, hashCode));
  }

*/

}
