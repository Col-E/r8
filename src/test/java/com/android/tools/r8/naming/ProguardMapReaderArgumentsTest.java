// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticPosition;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.PositionMatcher.positionLine;
import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.mappinginformation.MappingInformationDiagnostics;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProguardMapReaderArgumentsTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ProguardMapReaderArgumentsTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testMethodCanParseMemberComments() throws IOException {
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    Reporter reporter = new Reporter(testDiagnosticMessages);
    String mapWithArgumentRemovalInformation =
        StringUtils.join(
            "\n",
            "android.constraint.Placeholder -> a.b.b.f:",
            "# Just a comment", // Regular comment
            "    int mContentId -> b",
            "# {}", // Valid JSON with no information
            "    147:161:void updatePreLayout(android.constraint.Layout) -> a",
            "# {foo:bar}}}", // Not valid JSON
            "    194:204:void updatePostMeasure(android.constraint.Layout) -> b",
            "# { 'id': 'bar' }", // Valid json but no handler for bar.
            "    194:204:void updatePostMeasure(android.constraint.Layout) -> c");
    ClassNameMapper cnm =
        ClassNameMapper.mapperFromString(mapWithArgumentRemovalInformation, reporter);
    ClassNamingForNameMapper classNaming = cnm.getClassNaming("a.b.b.f");
    assertNotNull(classNaming);
    testDiagnosticMessages.assertOnlyInfos();
    testDiagnosticMessages
        .assertInfosMatch(
            ImmutableList.of(
                allOf(
                    diagnosticMessage(containsString("Could not locate 'id'")),
                    diagnosticPosition(positionLine(4))),
                allOf(
                    diagnosticMessage(containsString("Could not find a handler for bar")),
                    diagnosticPosition(positionLine(8)))))
        .assertAllInfosMatch(diagnosticType(MappingInformationDiagnostics.class));
  }
}
