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

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8CommandParser;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MapIdTemplateTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  @Test
  public void testNoVariables() throws Exception {
    String template = "my-build-id";
    assertEquals(template, compileWithMapIdTemplate(template));
  }

  @Test
  public void testInvalidVariable() {
    parameters.assumeIsOrSimulateNoneRuntime();
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    parseMapIdTemplate("my-%build-id", messages);
    messages
        .assertOnlyErrors()
        .assertErrorsMatch(
            diagnosticMessage(containsString("Invalid template variable starting with %bu")));
  }

  @Test
  public void testInvalidVariablesMix() {
    parameters.assumeIsOrSimulateNoneRuntime();
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    parseMapIdTemplate("my%%MAP_HASHJUNK", messages);
    messages
        .assertOnlyErrors()
        .assertErrorsMatch(
            diagnosticMessage(containsString("Invalid template variable starting with %%MAP_")));
  }

  @Test
  public void testNoEscape() {
    parameters.assumeIsOrSimulateNoneRuntime();
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
    return getMapId(
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class)
            .addKeepMainRule(TestClass.class)
            .setMapIdTemplate(template)
            .setMinApi(parameters)
            .compile());
  }

  private void parseMapIdTemplate(String template, DiagnosticsHandler handler) {
    R8CommandParser.parse(new String[] {"--map-id-template", template}, Origin.unknown(), handler);
  }

  private String getMapId(R8TestCompileResult compileResult) {
    String mapping = compileResult.getProguardMap();
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
