// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.ProguardMapReader.ParseException;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class ProguardMapReaderTest extends TestBase {

  private static final String ROOT = ToolHelper.EXAMPLES_BUILD_DIR;
  private static final String EXAMPLE_MAP = "throwing/throwing.map";
  private static final String EXAMPLE_MAP_WITH_PACKAGE_INFO =
      "dagger.android.package-info -> dagger.android.package-info:\n";

  @Test
  public void parseThrowingMap() throws IOException {
    ClassNameMapper.mapperFromFile(Paths.get(ROOT, EXAMPLE_MAP));
  }

  @Test
  public void parseQuestionMarkMethod() throws IOException {
    // Regression test for b/120856784
    String mapping =
        "com.c.c.b -> com.c.c.b:\n" +
            "    1287:1287:int ?(int,int) -> ?";
    ClassNameMapper.mapperFromString(mapping);

    // From some other proguard generated map
    mapping = "com.moat.analytics.mobile.cha.b -> com.moat.analytics.mobile.cha.b:\n"
        + "    com.moat.analytics.mobile.cha.MoatAdEventType[] ? -> ?\n"
        + "    java.util.HashMap ? -> ?\n"
        + "    java.util.HashSet ?? -> ??\n";
    ClassNameMapper.mapperFromString(mapping);
  }


  @Test
  public void roundTripTest() throws IOException {
    ClassNameMapper firstMapper =
        ClassNameMapper.mapperFromFile(Paths.get(ROOT, EXAMPLE_MAP)).sorted();
    ClassNameMapper secondMapper =
        ClassNameMapper.mapperFromString(firstMapper.toString()).sorted();
    Assert.assertEquals(firstMapper, secondMapper);
  }

  @Test
  public void roundTripTestWithLeadingBOM() throws IOException {
    ClassNameMapper firstMapper =
        ClassNameMapper.mapperFromFile(Paths.get(ROOT, EXAMPLE_MAP)).sorted();
    assertTrue(firstMapper.toString().charAt(0) != StringUtils.BOM);
    ClassNameMapper secondMapper =
        ClassNameMapper.mapperFromString(StringUtils.BOM + firstMapper.toString()).sorted();
    assertTrue(secondMapper.toString().charAt(0) != StringUtils.BOM);
    Assert.assertEquals(firstMapper, secondMapper);
    byte[] bytes = Files.readAllBytes(Paths.get(ROOT, EXAMPLE_MAP));
    assertNotEquals(0xef, Byte.toUnsignedLong(bytes[0]));
    Path mapFileWithBOM = writeTextToTempFile(StringUtils.BOM + firstMapper.toString());
    bytes = Files.readAllBytes(mapFileWithBOM);
    assertEquals(0xef, Byte.toUnsignedLong(bytes[0]));
    assertEquals(0xbb, Byte.toUnsignedLong(bytes[1]));
    assertEquals(0xbf, Byte.toUnsignedLong(bytes[2]));
    ClassNameMapper thirdMapper = ClassNameMapper.mapperFromFile(mapFileWithBOM).sorted();
    assertTrue(thirdMapper.toString().charAt(0) != StringUtils.BOM);
    Assert.assertEquals(firstMapper, thirdMapper);
  }

  @Test
  public void roundTripTestWithMultipleBOMsAndWhitespace() throws IOException {
    List<String> ws =
        ImmutableList.of(
            "",
            " ",
            "  ",
            "\t ",
            " \t",
            "" + StringUtils.BOM,
            StringUtils.BOM + " " + StringUtils.BOM);
    for (String whitespace : ws) {
      ClassNameMapper firstMapper =
          ClassNameMapper.mapperFromFile(Paths.get(ROOT, EXAMPLE_MAP)).sorted();
      assertTrue(firstMapper.toString().charAt(0) != StringUtils.BOM);
      StringBuilder buildWithWhitespace = new StringBuilder();
      char prevChar = '\0';
      for (char c : firstMapper.toString().toCharArray()) {
        if (c == ':' || c == ' ') {
          buildWithWhitespace.append(whitespace);
          buildWithWhitespace.append(c);
          buildWithWhitespace.append(whitespace);
        } else if (c == '-' || c == '(') {
          buildWithWhitespace.append(whitespace);
          buildWithWhitespace.append(c);
        } else if (c == '>' && prevChar == '-') {
          buildWithWhitespace.append(c);
          buildWithWhitespace.append(whitespace);
        } else {
          buildWithWhitespace.append(c);
        }
        prevChar = c;
      }
      ClassNameMapper secondMapper =
          ClassNameMapper.mapperFromString(buildWithWhitespace.toString()).sorted();
      assertFalse(firstMapper.toString().contains("" + StringUtils.BOM));
      Assert.assertEquals(firstMapper, secondMapper);
      byte[] bytes = Files.readAllBytes(Paths.get(ROOT, EXAMPLE_MAP));
      assertNotEquals(0xef, Byte.toUnsignedLong(bytes[0]));
      Path mapFileWithBOM = writeTextToTempFile(StringUtils.BOM + firstMapper.toString());
      ClassNameMapper thirdMapper = ClassNameMapper.mapperFromFile(mapFileWithBOM).sorted();
      assertTrue(thirdMapper.toString().charAt(0) != StringUtils.BOM);
      Assert.assertEquals(firstMapper, thirdMapper);
    }
  }

  @Test
  public void parseIdentifierArrowAmbiguity1() throws IOException {
    ClassNameMapper mapper = ClassNameMapper.mapperFromString("a->b:");
    ClassNameMapper.Builder builder = ClassNameMapper.builder();
    builder.classNamingBuilder("b", "a", Position.UNKNOWN);
    Assert.assertEquals(builder.build(), mapper);
  }

  @Test
  public void parseIdentifierArrowAmbiguity2() throws IOException {
    ClassNameMapper mapper = ClassNameMapper.mapperFromString("-->b:");
    ClassNameMapper.Builder builder = ClassNameMapper.builder();
    builder.classNamingBuilder("b", "-", Position.UNKNOWN);
    Assert.assertEquals(builder.build(), mapper);
  }

  @Test
  public void parseMapWithPackageInfo() throws IOException {
    ClassNameMapper mapper = ClassNameMapper.mapperFromString(EXAMPLE_MAP_WITH_PACKAGE_INFO);
    assertEquals(1, mapper.getObfuscatedToOriginalMapping().original.size());
  }

  @Test
  public void testSingleCases() throws IOException {
    List<String> ss =
        ImmutableList.of(
            /* */ "a.b.C -> d.e.F:\n" //
                + "    void a() -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void a() -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:2:void a() -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void a():2:2 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:2:void a():11:12 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void a():11:11 -> b\n" //
                + "    1:1:void c():21 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:2:void a():11:12 -> b\n"
                + "    1:2:void c():21 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void a(int):11:11 -> b\n" //
                + "    1:1:void c():21 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void g.a(int):11:11 -> b\n" //
                + "    1:1:void c():21 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void a(int):11:11 -> b\n" //
                + "    2:2:void c():21:21 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void a():11:11 -> b\n" //
                + "    1:1:void c():21:21 -> d\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    2:2:void f1():11:11 -> b\n" //
                + "    2:2:void f2():21 -> b\n" //
                + "    2:2:void f3():21 -> b\n" //
                + "    2:2:void f4():22 -> b\n" //
                + "    2:2:void f5():21 -> b\n" //
                + "    3:3:void f6():12:34 -> c\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    2:2:void f1():11:11 -> b\n" //
                + "    2:2:void f2():21 -> b\n" //
                + "    2:2:void f3() -> b\n" //
                + "    2:2:void f5():21 -> b\n" //
                + "    3:3:void f6() -> c\n");

    for (String s : ss) {
      ClassNameMapper cnm = ClassNameMapper.mapperFromString(s);
      String result = cnm.toString();
      Assert.assertEquals(s, result);
    }
  }

  @Test()
  public void testCommentLineBeforeAnyClassMappings() throws IOException {
    String mapping =
        StringUtils.lines(
            "# {'id':'some.namespace.here','unknownField':'Hi There'}", "foo.bar.baz -> a:");
    ClassNameMapper.mapperFromString(mapping);
  }

  // TODO(b/179666867): Should not fail.
  @Test(expected = ParseException.class)
  public void testCommentLineOnClassMapping() throws IOException {
    ClassNameMapper.mapperFromString("foo.bar.qux -> b: #  Some comment here");
  }

  // TODO(b/179666867): Should not fail.
  @Test(expected = ParseException.class)
  public void testJsonCommentLineOnClassMapping() throws IOException {
    ClassNameMapper.mapperFromString(
        "foo.bar.baz -> a: # {'id':'same.class.namespace.here','frame':'foo'}");
  }

  // TODO(b/179666867): Should not fail.
  @Test(expected = ParseException.class)
  public void testCommentLinesOnMethodMappingFiles() throws IOException {
    ClassNameMapper.mapperFromString(
        StringUtils.lines(
            "foo.bar.qux -> b:",
            "    1:10:void error(com.android.tools.r8.Diagnostic) -> error # Some comment here"));
  }

  // TODO(b/179666867): Should not fail.
  @Test(expected = ParseException.class)
  public void testJsonCommentLinesOnMethodMappingFiles() throws IOException {
    ClassNameMapper.mapperFromString(
        StringUtils.lines(
            "foo.bar.qux -> b:",
            "    1:10:void error(com.android.tools.r8.Diagnostic) -> error #"
                + " {'id':'same.frame.namespace.here','frame':'bar'}"));
  }

  @Test()
  public void testUnknownNamespaceComments() throws IOException {
    String mappingWithComments =
        StringUtils.lines(
            "foo.bar.baz -> a:",
            "# {'id':'some.other.namespace.here','fileName':'Class.kt'}",
            "    1:10:void error(com.android.tools.r8.Diagnostic) -> error",
            "# {'id':'some.line.namespace.here','fileName':'Class.kt'}",
            "foo.bar.qux -> b:",
            "# {'id':'some.final.namespace.thing','foo':'Hello World'}");
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    ClassNameMapper.mapperFromString(mappingWithComments, testDiagnosticMessages);
    testDiagnosticMessages.assertOnlyInfos();
    testDiagnosticMessages.assertInfosMatch(
        ImmutableList.of(
            diagnosticMessage(
                containsString("Could not find a handler for some.other.namespace.here")),
            diagnosticMessage(
                containsString("Could not find a handler for some.line.namespace.here")),
            diagnosticMessage(
                containsString("Could not find a handler for some.final.namespace.thing"))));
  }
}
