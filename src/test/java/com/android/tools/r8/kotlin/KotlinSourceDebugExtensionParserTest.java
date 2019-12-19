// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.Position;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.Result;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinSourceDebugExtensionParserTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KotlinSourceDebugExtensionParserTest(TestParameters parameters) {}

  @Test
  public void testParsingEmpty() {
    assertNull(KotlinSourceDebugExtensionParser.parse(null));
  }

  @Test
  public void testParsingNoInlineSources() {
    String annotationData =
        StringUtils.join(
            "\n",
            "SMAP",
            "EnumSwitch.kt",
            "Kotlin",
            "*S Kotlin",
            "*F",
            "+ 1 EnumSwitch.kt",
            "enumswitch/EnumSwitchKt",
            "*L",
            "1#1,38:1",
            "*E");
    Result result = KotlinSourceDebugExtensionParser.parse(annotationData);
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(1, (int) result.lookup(1).getKey());
    Position position = result.lookup(1).getValue();
    assertEquals("EnumSwitch.kt", position.getSource().getFileName());
    assertEquals("enumswitch/EnumSwitchKt", position.getSource().getPath());
    assertEquals(1, position.getRange().from);
    assertEquals(38, position.getRange().to);
  }

  @Test
  public void testParsingSimpleStrata() {
    // Taken from src/test/examplesKotlin/retrace/mainKt
    String annotationData =
        StringUtils.join(
            "\n",
            "SMAP",
            "Main.kt",
            "Kotlin",
            "*S Kotlin",
            "*F",
            "+ 1 Main.kt",
            "retrace/MainKt",
            "+ 2 InlineFunction.kt",
            "retrace/InlineFunctionKt",
            "+ 3 InlineFunction.kt",
            "retrace/InlineFunction",
            "*L",
            "1#1,22:1",
            "7#2:23",
            "12#3:24",
            "*E",
            "*S KotlinDebug",
            "*F",
            "+ 1 Main.kt",
            "retrace/MainKt",
            "*L",
            "12#1:23",
            "18#1:24",
            "*E");
    Result result = KotlinSourceDebugExtensionParser.parse(annotationData);
    assertNotNull(result);
    assertEquals(3, result.size());
    assertEquals(1, (int) result.lookup(1).getKey());
    assertEquals(23, (int) result.lookup(23).getKey());
    assertEquals(24, (int) result.lookup(24).getKey());

    // Check that files are correctly parsed.
    Position pos1 = result.lookup(1).getValue();
    assertEquals("Main.kt", pos1.getSource().getFileName());
    assertEquals("retrace/MainKt", pos1.getSource().getPath());

    Position pos2 = result.lookup(23).getValue();
    assertEquals("InlineFunction.kt", pos2.getSource().getFileName());
    assertEquals("retrace/InlineFunctionKt", pos2.getSource().getPath());

    Position pos3 = result.lookup(24).getValue();
    assertEquals("InlineFunction.kt", pos3.getSource().getFileName());
    assertEquals("retrace/InlineFunction", pos3.getSource().getPath());

    // Check that the inline positions can be traced.
    assertEquals(1, pos1.getRange().from);
    assertEquals(22, pos1.getRange().to);

    assertEquals(7, pos2.getRange().from);
    assertEquals(7, pos2.getRange().to);

    assertEquals(12, pos3.getRange().from);
    assertEquals(12, pos3.getRange().to);
  }

  @Test
  public void testNoKotlinHeader() {
    String annotationData =
        StringUtils.join(
            "\n",
            "SMAP",
            "Main.kt",
            "Kotlin",
            "*F",
            "+ 1 Main.kt",
            "retrace/MainKt",
            "+ 2 InlineFunction.kt",
            "retrace/InlineFunctionKt",
            "+ 3 InlineFunction.kt",
            "retrace/InlineFunction",
            "*L",
            "1#1,22:1",
            "7#2:23",
            "12#3:24",
            "*E",
            "*S KotlinDebug",
            "*F",
            "+ 1 Main.kt",
            "retrace/MainKt",
            "*L",
            "12#1:23",
            "18#1:24",
            "*E");
    assertNull(KotlinSourceDebugExtensionParser.parse(annotationData));
  }

  @Test
  public void testIncompleteFileBlock() {
    String annotationData =
        StringUtils.join(
            "\n",
            "SMAP",
            "Main.kt",
            "Kotlin",
            "*F",
            "+ 1 Main.kt",
            "+ 2 InlineFunction.kt",
            "retrace/InlineFunctionKt",
            "+ 3 InlineFunction.kt",
            "retrace/InlineFunction",
            "*L",
            "1#1,22:1",
            "7#2:23",
            "12#3:24",
            "*E",
            "*S KotlinDebug",
            "*F",
            "+ 1 Main.kt",
            "retrace/MainKt",
            "*L",
            "12#1:23",
            "18#1:24",
            "*E");
    assertNull(KotlinSourceDebugExtensionParser.parse(annotationData));
  }

  @Test
  public void testDuplicateFileIndex() {
    String annotationData =
        StringUtils.join(
            "\n",
            "SMAP",
            "Main.kt",
            "Kotlin",
            "*S Kotlin",
            "*F",
            "+ 1 Main.kt",
            "retrace/MainKt",
            "+ 2 InlineFunction.kt",
            "retrace/InlineFunctionKt",
            "+ 1 InlineFunction.kt",
            "retrace/InlineFunction",
            "*L",
            "1#1,22:1",
            "7#2:23",
            "12#3:24",
            "*E",
            "*S KotlinDebug",
            "*F",
            "+ 1 Main.kt",
            "retrace/MainKt",
            "*L",
            "12#1:23",
            "18#1:24",
            "*E");
    assertNull(KotlinSourceDebugExtensionParser.parse(annotationData));
  }

  @Test
  public void testNoDebugEntries() {
    String annotationData =
        StringUtils.join(
            "\n",
            "SMAP",
            "Main.kt",
            "Kotlin",
            "*S Kotlin",
            "*F",
            "+ 1 Main.kt",
            "retrace/MainKt",
            "+ 2 InlineFunction.kt",
            "retrace/InlineFunctionKt",
            "+ 3 InlineFunction.kt",
            "retrace/InlineFunction",
            "*E");
    assertNull(KotlinSourceDebugExtensionParser.parse(annotationData));
  }

  @Test
  public void testInvalidRanges() {
    String annotationData =
        StringUtils.join(
            "\n",
            "SMAP",
            "Main.kt",
            "Kotlin",
            "*S Kotlin",
            "*F",
            "+ 1 Main.kt",
            "retrace/MainKt",
            "+ 2 InlineFunction.kt",
            "retrace/InlineFunctionKt",
            "+ 3 InlineFunction.kt",
            "retrace/InlineFunction",
            "*L",
            "1#bar,22:1",
            "7#2:23",
            "12#3:foo",
            "*E");
    assertNull(KotlinSourceDebugExtensionParser.parse(annotationData));
  }

  @Test
  public void testNoSourceFileForEntry() {
    String annotationData =
        StringUtils.join(
            "\n",
            "SMAP",
            "Main.kt",
            "Kotlin",
            "*S Kotlin",
            "*F",
            "+ 1 Main.kt",
            "retrace/MainKt",
            "+ 2 InlineFunction.kt",
            "retrace/InlineFunctionKt",
            "+ 3 InlineFunction.kt",
            "retrace/InlineFunction",
            "*L",
            "1#1,22:1",
            "7#2:23",
            "12#4:24", // <-- non-existing file index
            "*E");
    assertNull(KotlinSourceDebugExtensionParser.parse(annotationData));
  }

  @Test
  public void testRanges() {
    String annotationData =
        StringUtils.join(
            "\n",
            "SMAP",
            "Main.kt",
            "Kotlin",
            "*S Kotlin",
            "*F",
            "+ 1 Main.kt",
            "retrace/MainKt",
            "+ 2 InlineFunction.kt",
            "retrace/InlineFunctionKt",
            "+ 3 InlineFunction.kt",
            "retrace/InlineFunction",
            "*L",
            "1#1,22:1",
            "7#2,1:23",
            "12#3,2:24",
            "*E",
            "*S KotlinDebug",
            "*F",
            "+ 1 Main.kt",
            "retrace/MainKt",
            "*L",
            "12#1:23",
            "18#1:24",
            "*E");
    Result parsedResult = KotlinSourceDebugExtensionParser.parse(annotationData);
    assertNotNull(parsedResult);
    assertEquals(24, (int) parsedResult.lookup(25).getKey());
    Position value = parsedResult.lookup(25).getValue();
    assertEquals(12, value.getRange().from);
    assertEquals(13, value.getRange().to);
  }
}
