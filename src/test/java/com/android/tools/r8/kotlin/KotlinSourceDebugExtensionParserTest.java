// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.Position;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.Result;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.Source;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Ignore;
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
  @Ignore("b/145985445")
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
    assertEquals(1, result.getFiles().size());
    Source source = result.getFiles().get(1);
    assertEquals("EnumSwitch.kt", source.getFileName());
    assertEquals("enumswitch/EnumSwitchKt", source.getPath());
    assertTrue(result.getPositions().containsKey(1));
    Position position = result.getPositions().get(1);
    assertEquals(source, position.getSource());
    assertEquals(1, position.getRange().from);
    assertEquals(38, position.getRange().to);
  }

  @Test
  @Ignore("b/145985445")
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
    assertEquals(3, result.getFiles().size());
    // Check that files are correctly parsed.
    Source source1 = result.getFiles().get(1);
    assertEquals("Main.kt", source1.getFileName());
    assertEquals("retrace/MainKt", source1.getPath());

    Source source2 = result.getFiles().get(2);
    assertEquals("InlineFunction.kt", source2.getFileName());
    assertEquals("retrace/InlineFunctionKt", source2.getPath());

    Source source3 = result.getFiles().get(3);
    assertEquals("InlineFunction.kt", source3.getFileName());
    assertEquals("retrace/InlineFunction", source3.getPath());

    // Check that the inline positions can be traced.
    assertTrue(result.getPositions().containsKey(23));
    Position position1 = result.getPositions().get(23);
    assertEquals(source2, position1.getSource());
    assertEquals(7, position1.getRange().from);
    assertEquals(7, position1.getRange().to);

    assertTrue(result.getPositions().containsKey(24));
    Position position2 = result.getPositions().get(24);
    assertEquals(source3, position2.getSource());
    assertEquals(12, position2.getRange().from);
    assertEquals(12, position2.getRange().to);
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
    Result parsedResult = KotlinSourceDebugExtensionParser.parse(annotationData);
    assertNotNull(parsedResult);

    assertEquals(2, parsedResult.getPositions().size());
    assertTrue(parsedResult.getPositions().containsKey(1));
    assertTrue(parsedResult.getPositions().containsKey(23));
    assertFalse(parsedResult.getPositions().containsKey(24));
  }
}
