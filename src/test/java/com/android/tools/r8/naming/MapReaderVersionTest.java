// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.io.CharSource;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MapReaderVersionTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public MapReaderVersionTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static ClassNameMapper read(DiagnosticsHandler diagnosticsHandler, String... lines)
      throws IOException {
    return ClassNameMapper.mapperFromBufferedReader(
        CharSource.wrap(StringUtils.joinLines(lines)).openBufferedStream(),
        diagnosticsHandler,
        false,
        true,
        false);
  }

  private static ClassNameMapper read(String... lines) throws IOException {
    return read(null, lines);
  }

  @Test
  public void testNoVersion() throws IOException {
    ClassNameMapper mapper =
        read("pkg.Foo -> a.a:", "# { id: \"com.android.tools.r8.synthesized\" }");
    assertMapping("a.a", "pkg.Foo", false, mapper);
  }

  @Test
  public void testExperimentalVersion() throws IOException {
    ClassNameMapper mapper =
        read(
            "# { id: 'com.android.tools.r8.mapping', version: 'experimental' }",
            "pkg.Foo -> a.a:",
            "# { id: 'com.android.tools.r8.synthesized' }");
    assertMapping("a.a", "pkg.Foo", true, mapper);
  }

  @Test
  public void testConcatMapFiles() throws IOException {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    ClassNameMapper mapper =
        read(
            diagnostics,
            // Default map-version is none.
            "pkg.Foo -> a.a:",
            "# { id: 'com.android.tools.r8.synthesized' }",
            // Section with map-version experimental.
            "# { id: 'com.android.tools.r8.mapping', version: 'experimental' }",
            "pkg.Bar -> a.b:",
            "# { id: 'com.android.tools.r8.synthesized' }",
            // Section reverting map-version back to none (to support tooling that
            // concatenates).
            "# { id: 'com.android.tools.r8.mapping', version: 'none' }",
            "pkg.Baz -> a.c:",
            "# { id: 'com.android.tools.r8.synthesized' }");
    diagnostics.assertNoMessages();
    assertMapping("a.a", "pkg.Foo", false, mapper);
    assertMapping("a.b", "pkg.Bar", true, mapper);
    assertMapping("a.c", "pkg.Baz", false, mapper);
  }

  private void assertMapping(
      String finalName, String originalName, boolean isSynthesized, ClassNameMapper mapper) {
    ClassNamingForNameMapper naming = mapper.getClassNaming(finalName);
    assertEquals(originalName, naming.originalName);
    Assert.assertEquals(isSynthesized, isCompilerSynthesized(naming));
  }

  private boolean isCompilerSynthesized(ClassNamingForNameMapper naming) {
    return naming.getAdditionalMappingInfo().stream()
        .anyMatch(MappingInformation::isCompilerSynthesizedMappingInformation);
  }
}
