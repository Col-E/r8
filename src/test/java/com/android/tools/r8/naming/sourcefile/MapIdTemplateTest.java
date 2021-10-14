// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.sourcefile;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.R8CommandParser;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MapIdTemplateTest extends TestBase {

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), Backend.values());
  }

  private final Backend backend;

  public MapIdTemplateTest(TestParameters parameters, Backend backend) {
    parameters.assertNoneRuntime();
    this.backend = backend;
  }

  @Test
  public void testNoVariables() throws Exception {
    String template = "my-build-id";
    assertEquals(template, compileWithMapIdTemplate(template));
  }

  @Test
  public void testInvalidVariable() {
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    parseMapIdTemplate("my-%build-id", messages);
    messages
        .assertOnlyErrors()
        .assertErrorsMatch(
            diagnosticMessage(containsString("Invalid template variable starting with %bu")));
  }

  @Test
  public void testInvalidVariablesMix() {
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    parseMapIdTemplate("my%%MAP_HASHJUNK", messages);
    messages
        .assertOnlyErrors()
        .assertErrorsMatch(
            diagnosticMessage(containsString("Invalid template variable starting with %%MAP_")));
  }

  @Test
  public void testNoEscape() {
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    parseMapIdTemplate("my%%buildid", messages);
    messages
        .assertOnlyErrors()
        .assertErrorsMatch(
            Arrays.asList(
                diagnosticMessage(containsString("Invalid template variable starting with %%b")),
                diagnosticMessage(containsString("Invalid template variable starting with %b"))));
  }

  @Test
  public void testMapHash() throws Exception {
    String template = "mybuildid %MAP_HASH";
    String actual = compileWithMapIdTemplate(template);
    assertThat(actual, startsWith("mybuildid "));
    assertThat(actual, not(containsString("%")));
    assertEquals("mybuildid ".length() + 64, actual.length());
  }

  @Test
  public void testMultiple() throws Exception {
    String template = "hash %MAP_HASH hash %MAP_HASH";
    String actual = compileWithMapIdTemplate(template);
    assertEquals("hash  hash ".length() + 2 * 64, actual.length());
  }

  private String compileWithMapIdTemplate(String template) throws Exception {
    Path out = temp.newFolder().toPath().resolve("out.jar");
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    StringBuilder mapping = new StringBuilder();
    R8.run(
        parseMapIdTemplate(template, messages)
            .addProguardConfiguration(
                Arrays.asList("-keep class * { *; }", "-dontwarn " + typeName(TestClass.class)),
                Origin.unknown())
            .setProgramConsumer(
                backend.isCf()
                    ? new ArchiveConsumer(out)
                    : new DexIndexedConsumer.ArchiveConsumer(out))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            // TODO(b/201269335): What should be the expected result when no map is created?
            .setProguardMapConsumer((content, handler) -> mapping.append(content))
            .build());
    messages.assertNoMessages();
    return getMapId(mapping.toString());
  }

  private Builder parseMapIdTemplate(String template, DiagnosticsHandler handler) {
    return R8CommandParser.parse(
        new String[] {"--map-id-template", template}, Origin.unknown(), handler);
  }

  private String getMapId(String mapping) {
    String lineHeader = "# pg_map_id: ";
    int i = mapping.indexOf(lineHeader);
    assertTrue(i >= 0);
    int start = i + lineHeader.length();
    int end = mapping.indexOf('\n', start);
    return mapping.substring(start, end);
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello world");
    }
  }
}
