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

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.R8CommandParser;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SourceFileTemplateTest extends TestBase {

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), Backend.values());
  }

  private final Backend backend;

  public SourceFileTemplateTest(TestParameters parameters, Backend backend) {
    parameters.assertNoneRuntime();
    this.backend = backend;
  }

  @Test
  public void testNoVariables() throws Exception {
    String template = "MySourceFile";
    assertEquals(
        template,
        new CodeInspector(compileWithSourceFileTemplate(template))
            .clazz(TestClass.class)
            .getDexProgramClass()
            .getSourceFile()
            .toString());
  }

  @Test
  public void testInvalidVariables() {
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    parseSourceFileTemplate("My%Source%File", messages);
    messages
        .assertOnlyErrors()
        .assertErrorsMatch(
            Arrays.asList(
                diagnosticMessage(containsString("Invalid template variable starting with %So")),
                diagnosticMessage(containsString("Invalid template variable starting with %Fi"))));
  }

  @Test
  public void testInvalidVariablesMix() {
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    parseSourceFileTemplate("My%%MAP_IDJUNK", messages);
    messages
        .assertOnlyErrors()
        .assertErrorsMatch(
            diagnosticMessage(containsString("Invalid template variable starting with %%MAP_")));
  }

  @Test
  public void testNoEscape() {
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    parseSourceFileTemplate("My%%SourceFile", messages);
    messages
        .assertOnlyErrors()
        .assertErrorsMatch(
            Arrays.asList(
                diagnosticMessage(containsString("Invalid template variable starting with %%S")),
                diagnosticMessage(containsString("Invalid template variable starting with %So"))));
  }

  @Test
  public void testMapId() throws Exception {
    String template = "MySourceFile %MAP_ID";
    String actual =
        new CodeInspector(compileWithSourceFileTemplate(template))
            .clazz(TestClass.class)
            .getDexProgramClass()
            .getSourceFile()
            .toString();
    assertThat(actual, startsWith("MySourceFile "));
    assertThat(actual, not(containsString("%")));
    assertEquals("MySourceFile ".length() + 7, actual.length());
  }

  @Test
  public void testMapHash() throws Exception {
    String template = "MySourceFile %MAP_HASH";
    String actual =
        new CodeInspector(compileWithSourceFileTemplate(template))
            .clazz(TestClass.class)
            .getDexProgramClass()
            .getSourceFile()
            .toString();
    assertThat(actual, startsWith("MySourceFile "));
    assertThat(actual, not(containsString("%")));
    assertEquals("MySourceFile ".length() + 64, actual.length());
  }

  @Test
  public void testMultiple() throws Exception {
    String template = "id %MAP_ID hash %MAP_HASH id %MAP_ID hash %MAP_HASH";
    String actual =
        new CodeInspector(compileWithSourceFileTemplate(template))
            .clazz(TestClass.class)
            .getDexProgramClass()
            .getSourceFile()
            .toString();
    assertEquals("id  hash  id  hash ".length() + 2 * 7 + 2 * 64, actual.length());
  }

  private Path compileWithSourceFileTemplate(String template)
      throws IOException, CompilationFailedException {
    Path out = temp.newFolder().toPath().resolve("out.jar");
    TestDiagnosticMessagesImpl messages = new TestDiagnosticMessagesImpl();
    R8.run(
        parseSourceFileTemplate(template, messages)
            .addProguardConfiguration(
                Arrays.asList("-keep class * { *; }", "-dontwarn " + typeName(TestClass.class)),
                Origin.unknown())
            .setProgramConsumer(
                backend.isCf()
                    ? new ArchiveConsumer(out)
                    : new DexIndexedConsumer.ArchiveConsumer(out))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            // TODO(b/201269335): What should be the expected result when no map is created?
            .setProguardMapConsumer(StringConsumer.emptyConsumer())
            .build());
    messages.assertNoMessages();
    return out;
  }

  private Builder parseSourceFileTemplate(String template, DiagnosticsHandler handler) {
    return R8CommandParser.parse(
        new String[] {"--source-file-template", template}, Origin.unknown(), handler);
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello world");
    }
  }
}
