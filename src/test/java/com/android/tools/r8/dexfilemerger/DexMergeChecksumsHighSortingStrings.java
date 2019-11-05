// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexfilemerger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.ClassesChecksum;
import com.android.tools.r8.graph.DexItemFactory;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DexMergeChecksumsHighSortingStrings extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DexMergeChecksumsHighSortingStrings(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testChecksumWhitHighSortingStrings() throws Exception {
    Path dexArchive1 =
        testForD8()
            .addProgramClasses(TestClass1.class)
            .setMinApi(parameters.getApiLevel())
            .setMode(CompilationMode.DEBUG)
            .setIncludeClassesChecksum(true)
            .compile()
            .writeToZip();

    Path dexArchive2 =
        testForD8()
            .addProgramClasses(TestClass2.class)
            .setMinApi(parameters.getApiLevel())
            .setMode(CompilationMode.DEBUG)
            .setIncludeClassesChecksum(true)
            .compile()
            .writeToZip();

    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramFiles(dexArchive1)
        .setIncludeClassesChecksum(true)
        .run(parameters.getRuntime(), TestClass1.class)
        .assertSuccessWithOutputLines("Hello, \uDB3F\uDFFD");

    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramFiles(dexArchive2)
        .setIncludeClassesChecksum(true)
        .run(parameters.getRuntime(), TestClass2.class)
        .assertSuccessWithOutputLines("Hello, ~~~\u007f");

    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramFiles(dexArchive1, dexArchive2)
        .setIncludeClassesChecksum(true)
        .run(parameters.getRuntime(), TestClass2.class)
        .assertSuccessWithOutputLines("Hello, ~~~\u007f");
  }

  static class TestClass1 {
    // U+DFFFD which is very end of unassigned plane.
    private static final String STRING_SORTING_ABOVE_CHECKSUM_STRING = "\uDB3F\uDFFD";

    public static void main(String[] args) {
      System.out.println("Hello, " + STRING_SORTING_ABOVE_CHECKSUM_STRING);
    }
  }

  static class TestClass2 {
    private static final String STRING_SORTING_ABOVE_CHECKSUM_STRING = "~~~\u007f";

    public static void main(String[] args) {
      System.out.println("Hello, " + STRING_SORTING_ABOVE_CHECKSUM_STRING);
    }
  }

  @Test
  public void testChecksumMarkerComparison() {
    // z     {     |     }     ~     <DEL>
    // 0x7a  0x7b  0x7c  0x7d  0x7e  0x7f
    assertTrue('{' - 1 == 'z');
    assertTrue('{' + 1 == '|');
    assertTrue('~' - 1 == '}');

    DexItemFactory factory = new DexItemFactory();
    assertFalse(
        ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("\uDB3F\uDFFD")));
    assertFalse(
        ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~\uDB3F\uDFFD\"")));
    assertFalse(
        ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~~\uDB3F\uDFFD")));
    assertFalse(
        ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~~~\uDB3F\uDFFD")));
    assertFalse(ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("\u007f")));
    assertFalse(ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~\u007f")));
    assertFalse(ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~~\u007f")));
    assertFalse(
        ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~~~\u007f")));
    assertFalse(ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~~~|")));

    assertTrue(ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~~}")));
    assertTrue(ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~~")));
    assertTrue(ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~}")));
    assertTrue(ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~")));
    assertTrue(ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("}")));

    // False negatives.
    assertFalse(ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~~~")));
    assertFalse(ClassesChecksum.definitelyPrecedesChecksumMarker(factory.createString("~~~z")));
  }
}
