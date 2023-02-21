// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugarCfVersion extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public DesugarCfVersion(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path zip1 =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(hide_1_8)
            .setMinApi(parameters)
            .compile()
            .writeToZip();

    Path zip2 =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(hide_1_7)
            .setMinApi(parameters)
            .compile()
            .writeToZip();

    boolean canUseStaticAndDefaultInterfaceMethods =
        parameters
            .getApiLevel()
            .isGreaterThanOrEqualTo(TestBase.apiLevelWithDefaultInterfaceMethodsSupport());
    byte[] bytes1 = ByteStreams.toByteArray(Files.newInputStream(zip1));
    byte[] bytes2 = ByteStreams.toByteArray(Files.newInputStream(zip2));
    if (!canUseStaticAndDefaultInterfaceMethods) {
      assertArrayEquals(bytes1, bytes2);
    }

    assertEquals(CfVersion.V1_8, extractClassFileVersion(hide_1_8));
    assertEquals(CfVersion.V1_7, extractClassFileVersion(hide_1_7));
    assertEquals(
        canUseStaticAndDefaultInterfaceMethods ? CfVersion.V1_8 : CfVersion.V1_7,
        extractClassFileVersion(
            ZipUtils.readSingleEntry(zip1, "com/google/android/gms/common/internal/Hide.class")));
    assertEquals(
        CfVersion.V1_7,
        extractClassFileVersion(
            ZipUtils.readSingleEntry(zip2, "com/google/android/gms/common/internal/Hide.class")));
  }

  /*
   Class file bytes for:

   public interface com.google.android.gms.common.internal.Hide extends java.lang.annotation.Annotation
     minor version: 0
     major version: 52
     flags: (0x2601) ACC_PUBLIC, ACC_INTERFACE, ACC_ABSTRACT, ACC_ANNOTATION
     this_class: #1                          // com/google/android/gms/common/internal/Hide
     super_class: #2                         // java/lang/Object
     interfaces: 1, fields: 0, methods: 0, attributes: 2
   Constant pool:
      #1 = Class              #19            // com/google/android/gms/common/internal/Hide
      #2 = Class              #20            // java/lang/Object
      #3 = Class              #21            // java/lang/annotation/Annotation
      #4 = Utf8               SourceFile
      #5 = Utf8               Hide.java
      #6 = Utf8               RuntimeVisibleAnnotations
      #7 = Utf8               Ljava/lang/annotation/Target;
      #8 = Utf8               value
      #9 = Utf8               Ljava/lang/annotation/ElementType;
     #10 = Utf8               TYPE
     #11 = Utf8               FIELD
     #12 = Utf8               METHOD
     #13 = Utf8               CONSTRUCTOR
     #14 = Utf8               PACKAGE
     #15 = Utf8               Ljava/lang/annotation/Retention;
     #16 = Utf8               Ljava/lang/annotation/RetentionPolicy;
     #17 = Utf8               CLASS
     #18 = Utf8               Ljava/lang/annotation/Documented;
     #19 = Utf8               com/google/android/gms/common/internal/Hide
     #20 = Utf8               java/lang/Object
     #21 = Utf8               java/lang/annotation/Annotation
   {
   }
   SourceFile: "Hide.java"
   RuntimeVisibleAnnotations:
     0: #7(#8=[e#9.#10,e#9.#11,e#9.#12,e#9.#13,e#9.#14])
       java.lang.annotation.Target(
         value=[Ljava/lang/annotation/ElementType;.TYPE,Ljava/lang/annotation/ElementType;.FIELD,Ljava/lang/annotation/ElementType;.METHOD,Ljava/lang/annotation/ElementType;.CONSTRUCTOR,Ljava/lang/annotation/ElementType;.PACKAGE]
       )
     1: #15(#8=e#16.#17)
       java.lang.annotation.Retention(
         value=Ljava/lang/annotation/RetentionPolicy;.CLASS
       )
     2: #18()
       java.lang.annotation.Documented

  */

  private static byte[] hide_1_8 =
      new byte[] {
        -54, -2, -70, -66, 0, 0, 0, 52, 0, 22, 7, 0, 19, 7, 0, 20,
        7, 0, 21, 1, 0, 10, 83, 111, 117, 114, 99, 101, 70, 105, 108, 101,
        1, 0, 9, 72, 105, 100, 101, 46, 106, 97, 118, 97, 1, 0, 25, 82,
        117, 110, 116, 105, 109, 101, 86, 105, 115, 105, 98, 108, 101, 65, 110, 110,
        111, 116, 97, 116, 105, 111, 110, 115, 1, 0, 29, 76, 106, 97, 118, 97,
        47, 108, 97, 110, 103, 47, 97, 110, 110, 111, 116, 97, 116, 105, 111, 110,
        47, 84, 97, 114, 103, 101, 116, 59, 1, 0, 5, 118, 97, 108, 117, 101,
        1, 0, 34, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 97, 110,
        110, 111, 116, 97, 116, 105, 111, 110, 47, 69, 108, 101, 109, 101, 110, 116,
        84, 121, 112, 101, 59, 1, 0, 4, 84, 89, 80, 69, 1, 0, 5, 70,
        73, 69, 76, 68, 1, 0, 6, 77, 69, 84, 72, 79, 68, 1, 0, 11,
        67, 79, 78, 83, 84, 82, 85, 67, 84, 79, 82, 1, 0, 7, 80, 65,
        67, 75, 65, 71, 69, 1, 0, 32, 76, 106, 97, 118, 97, 47, 108, 97,
        110, 103, 47, 97, 110, 110, 111, 116, 97, 116, 105, 111, 110, 47, 82, 101,
        116, 101, 110, 116, 105, 111, 110, 59, 1, 0, 38, 76, 106, 97, 118, 97,
        47, 108, 97, 110, 103, 47, 97, 110, 110, 111, 116, 97, 116, 105, 111, 110,
        47, 82, 101, 116, 101, 110, 116, 105, 111, 110, 80, 111, 108, 105, 99, 121,
        59, 1, 0, 5, 67, 76, 65, 83, 83, 1, 0, 33, 76, 106, 97, 118,
        97, 47, 108, 97, 110, 103, 47, 97, 110, 110, 111, 116, 97, 116, 105, 111,
        110, 47, 68, 111, 99, 117, 109, 101, 110, 116, 101, 100, 59, 1, 0, 43,
        99, 111, 109, 47, 103, 111, 111, 103, 108, 101, 47, 97, 110, 100, 114, 111,
        105, 100, 47, 103, 109, 115, 47, 99, 111, 109, 109, 111, 110, 47, 105, 110,
        116, 101, 114, 110, 97, 108, 47, 72, 105, 100, 101, 1, 0, 16, 106, 97,
        118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 1, 0,
        31, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 97, 110, 110, 111, 116,
        97, 116, 105, 111, 110, 47, 65, 110, 110, 111, 116, 97, 116, 105, 111, 110,
        38, 1, 0, 1, 0, 2, 0, 1, 0, 3, 0, 0, 0, 0, 0, 2,
        0, 4, 0, 0, 0, 2, 0, 5, 0, 6, 0, 0, 0, 51, 0, 3,
        0, 7, 0, 1, 0, 8, 91, 0, 5, 101, 0, 9, 0, 10, 101, 0,
        9, 0, 11, 101, 0, 9, 0, 12, 101, 0, 9, 0, 13, 101, 0, 9,
        0, 14, 0, 15, 0, 1, 0, 8, 101, 0, 16, 0, 17, 0, 18, 0,
        0
      };

  /*
   Class file bytes for:

   public interface com.google.android.gms.common.internal.Hide extends java.lang.annotation.Annotation
     minor version: 0
     major version: 51
     flags: (0x2601) ACC_PUBLIC, ACC_INTERFACE, ACC_ABSTRACT, ACC_ANNOTATION
     this_class: #2                          // com/google/android/gms/common/internal/Hide
     super_class: #4                         // java/lang/Object
     interfaces: 1, fields: 0, methods: 0, attributes: 2
   Constant pool:
      #1 = Utf8               com/google/android/gms/common/internal/Hide
      #2 = Class              #1             // com/google/android/gms/common/internal/Hide
      #3 = Utf8               java/lang/Object
      #4 = Class              #3             // java/lang/Object
      #5 = Utf8               java/lang/annotation/Annotation
      #6 = Class              #5             // java/lang/annotation/Annotation
      #7 = Utf8               Hide.java
      #8 = Utf8               Ljava/lang/annotation/Target;
      #9 = Utf8               value
     #10 = Utf8               Ljava/lang/annotation/ElementType;
     #11 = Utf8               TYPE
     #12 = Utf8               FIELD
     #13 = Utf8               METHOD
     #14 = Utf8               CONSTRUCTOR
     #15 = Utf8               PACKAGE
     #16 = Utf8               Ljava/lang/annotation/Retention;
     #17 = Utf8               Ljava/lang/annotation/RetentionPolicy;
     #18 = Utf8               CLASS
     #19 = Utf8               Ljava/lang/annotation/Documented;
     #20 = Utf8               SourceFile
     #21 = Utf8               RuntimeVisibleAnnotations
   {
   }
   SourceFile: "Hide.java"
   RuntimeVisibleAnnotations:
     0: #8(#9=[e#10.#11,e#10.#12,e#10.#13,e#10.#14,e#10.#15])
       java.lang.annotation.Target(
         value=[Ljava/lang/annotation/ElementType;.TYPE,Ljava/lang/annotation/ElementType;.FIELD,Ljava/lang/annotation/ElementType;.METHOD,Ljava/lang/annotation/ElementType;.CONSTRUCTOR,Ljava/lang/annotation/ElementType;.PACKAGE]
       )
     1: #16(#9=e#17.#18)
       java.lang.annotation.Retention(
         value=Ljava/lang/annotation/RetentionPolicy;.CLASS
       )
     2: #19()
       java.lang.annotation.Documented

  */
  private static byte[] hide_1_7 =
      new byte[] {
        -54, -2, -70, -66, 0, 0, 0, 51, 0, 22, 1, 0, 43, 99, 111, 109,
        47, 103, 111, 111, 103, 108, 101, 47, 97, 110, 100, 114, 111, 105, 100, 47,
        103, 109, 115, 47, 99, 111, 109, 109, 111, 110, 47, 105, 110, 116, 101, 114,
        110, 97, 108, 47, 72, 105, 100, 101, 7, 0, 1, 1, 0, 16, 106, 97,
        118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 7, 0,
        3, 1, 0, 31, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 97, 110,
        110, 111, 116, 97, 116, 105, 111, 110, 47, 65, 110, 110, 111, 116, 97, 116,
        105, 111, 110, 7, 0, 5, 1, 0, 9, 72, 105, 100, 101, 46, 106, 97,
        118, 97, 1, 0, 29, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47,
        97, 110, 110, 111, 116, 97, 116, 105, 111, 110, 47, 84, 97, 114, 103, 101,
        116, 59, 1, 0, 5, 118, 97, 108, 117, 101, 1, 0, 34, 76, 106, 97,
        118, 97, 47, 108, 97, 110, 103, 47, 97, 110, 110, 111, 116, 97, 116, 105,
        111, 110, 47, 69, 108, 101, 109, 101, 110, 116, 84, 121, 112, 101, 59, 1,
        0, 4, 84, 89, 80, 69, 1, 0, 5, 70, 73, 69, 76, 68, 1, 0,
        6, 77, 69, 84, 72, 79, 68, 1, 0, 11, 67, 79, 78, 83, 84, 82,
        85, 67, 84, 79, 82, 1, 0, 7, 80, 65, 67, 75, 65, 71, 69, 1,
        0, 32, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 97, 110, 110,
        111, 116, 97, 116, 105, 111, 110, 47, 82, 101, 116, 101, 110, 116, 105, 111,
        110, 59, 1, 0, 38, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47,
        97, 110, 110, 111, 116, 97, 116, 105, 111, 110, 47, 82, 101, 116, 101, 110,
        116, 105, 111, 110, 80, 111, 108, 105, 99, 121, 59, 1, 0, 5, 67, 76,
        65, 83, 83, 1, 0, 33, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103,
        47, 97, 110, 110, 111, 116, 97, 116, 105, 111, 110, 47, 68, 111, 99, 117,
        109, 101, 110, 116, 101, 100, 59, 1, 0, 10, 83, 111, 117, 114, 99, 101,
        70, 105, 108, 101, 1, 0, 25, 82, 117, 110, 116, 105, 109, 101, 86, 105,
        115, 105, 98, 108, 101, 65, 110, 110, 111, 116, 97, 116, 105, 111, 110, 115,
        38, 1, 0, 2, 0, 4, 0, 1, 0, 6, 0, 0, 0, 0, 0, 2,
        0, 20, 0, 0, 0, 2, 0, 7, 0, 21, 0, 0, 0, 51, 0, 3,
        0, 8, 0, 1, 0, 9, 91, 0, 5, 101, 0, 10, 0, 11, 101, 0,
        10, 0, 12, 101, 0, 10, 0, 13, 101, 0, 10, 0, 14, 101, 0, 10,
        0, 15, 0, 16, 0, 1, 0, 9, 101, 0, 17, 0, 18, 0, 19, 0,
        0
      };

  // Code for generating the lists above.
  public static void main(String[] args) throws IOException {
    Path file = Paths.get(args[0]);
    byte[] content = ByteStreams.toByteArray(Files.newInputStream(file));
    final int bytesPerLine = 16;
    for (int i = 0; i < content.length; i += bytesPerLine) {
      for (int j = 0; j < bytesPerLine && i + j < content.length; j++) {
        System.out.print(content[i + j] + ", ");
      }
      System.out.println();
    }
  }
}
