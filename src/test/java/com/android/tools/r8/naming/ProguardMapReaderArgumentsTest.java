// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticPosition;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.PositionMatcher.positionLine;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.mappinginformation.MappingInformationDiagnostics;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class ProguardMapReaderArgumentsTest {

  private Reporter reporter;
  private TestDiagnosticMessagesImpl testDiagnosticMessages;

  @Before
  public void setUp() {
    testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    reporter = new Reporter(testDiagnosticMessages);
  }

  @Test
  public void testMethodCanParseMemberComments() throws IOException {
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
                    diagnosticMessage(containsString("Not valid JSON")),
                    diagnosticPosition(positionLine(6))),
                allOf(
                    diagnosticMessage(containsString("Could not find a handler for bar")),
                    diagnosticPosition(positionLine(8)))))
        .assertAllInfosMatch(diagnosticType(MappingInformationDiagnostics.class));
  }

  @Test
  public void testMethodWillReportWhenParsingArgumentsChangedMemberComments() throws IOException {
    String mapWithArgumentRemovalInformation =
        StringUtils.join(
                "\n",
                "android.constraint.Placeholder -> a.b.b.f:",
                "# Just a comment", // Regular comment
                "    int mContentId -> b",
                // Valid JSON but missing signature
                "# { 'id': 'methodSignatureChanged', "
                    + "'signature': ['void', 'updatePreLayout', 'android.constraint.Layout'],"
                    + "'returnType': [], 'receiver': true, 'params': []}",
                "    147:161:void updatePreLayout(android.constraint.Layout) -> a",
                // Valid JSON but not an argumentsChanged object
                "# { 'id': 'methodSignatureChanged', "
                    + "'signature': ['void', 'updatePreLayout', 'android.constraint.Layout'],"
                    + "'returnType': [], 'receiver': true, 'params': []}",
                "    147:161:void updatePreLayout(android.constraint.Layout) -> a",
                // Valid JSON but not an arguments_changed object.
                "# { 'id': 'methodSignatureChanged', "
                    + "'signature': ['void','updatePreLayout','android.constraint.Layout'],"
                    + "'returnType': 'foo', 'receiver': 1, 'params': 'foo' }",
                "    194:204:void updatePostMeasure(android.constraint.Layout) -> a")
            .replace("'", "\"");
    ClassNameMapper cnm =
        ClassNameMapper.mapperFromString(mapWithArgumentRemovalInformation, reporter);
    ClassNamingForNameMapper classNaming = cnm.getClassNaming("a.b.b.f");
    assertNotNull(classNaming);
    testDiagnosticMessages.assertOnlyInfos();
    testDiagnosticMessages
        .assertInfosMatch(
            ImmutableList.of(
                allOf(
                    diagnosticMessage(containsString("Could not decode")),
                    diagnosticPosition(positionLine(4))),
                allOf(
                    diagnosticMessage(containsString("Could not decode")),
                    diagnosticPosition(positionLine(6))),
                allOf(
                    diagnosticMessage(containsString("Could not decode")),
                    diagnosticPosition(positionLine(8)))))
        .assertAllInfosMatch(diagnosticType(MappingInformationDiagnostics.class));
  }

  @Test
  public void testMethodCanParseArgumentRemoval() throws IOException {
    String mapWithArgumentRemovalInformation =
        StringUtils.lines(
            "android.constraint.Placeholder -> a.b.b.f:",
            "    int mContentId -> b",
            "# { 'id': 'methodSignatureChanged',"
                + "'signature': ["
                + "'void','updatePreLayout','android.constraint.Layout','String','int'],"
                + " 'returnType': 'void', 'receiver': true, 'params':[[1],[2]]}",
            "    147:161:void updatePreLayout(android.constraint.Layout,String,int) -> a",
            "# {'id':'methodSignatureChanged',"
                + "'signature': ["
                + "'void','updatePreMeasure','android.constraint.Layout','String','int'],"
                + "'returnType': 'void', 'receiver': true, 'params':[[3]]}",
            "    162:173:void updatePreMeasure(android.constraint.Layout,String,int) -> a",
            "    194:204:void updatePostMeasure(android.constraint.Layout,String,int) -> a");
    ClassNameMapper cnm = ClassNameMapper.mapperFromString(mapWithArgumentRemovalInformation);
    ClassNamingForNameMapper classNaming = cnm.getClassNaming("a.b.b.f");
    assertNotNull(classNaming);

    List<MemberNaming> members = classNaming.lookupByOriginalName("mContentId");
    assertFalse(members.isEmpty());
    MemberNaming fieldContentId = members.get(0);
    assertNotNull(fieldContentId);
    assertTrue(!fieldContentId.isMethodNaming());

    members = classNaming.lookupByOriginalName("updatePreLayout");
    assertFalse(members.isEmpty());
    MemberNaming updatePreLayout = members.get(0);
    assertNotNull(updatePreLayout);
    assertTrue(updatePreLayout.isMethodNaming());
    MethodSignature renamedPreLayout = (MethodSignature) updatePreLayout.getRenamedSignature();
    assertEquals(1, renamedPreLayout.parameters.length);
    assertEquals("int", renamedPreLayout.parameters[0]);

    members = classNaming.lookupByOriginalName("updatePreMeasure");
    assertFalse(members.isEmpty());
    MemberNaming updatePreMeasure = members.get(0);
    assertNotNull(updatePreMeasure);
    assertTrue(updatePreMeasure.isMethodNaming());
    MethodSignature renamedPreMeasure = (MethodSignature) updatePreMeasure.getRenamedSignature();
    assertEquals(2, renamedPreMeasure.parameters.length);
    assertEquals("android.constraint.Layout", renamedPreMeasure.parameters[0]);
    assertEquals("String", renamedPreMeasure.parameters[1]);

    members = classNaming.lookupByOriginalName("updatePostMeasure");
    assertFalse(members.isEmpty());
    MemberNaming updatePostMeasure = members.get(0);
    assertNotNull(updatePostMeasure);
    assertTrue(updatePostMeasure.isMethodNaming());
    MethodSignature renamedPostMeasure = (MethodSignature) updatePostMeasure.getRenamedSignature();
    assertEquals(3, renamedPostMeasure.parameters.length);
  }

  @Test
  public void testMethodCanParseArgumentChanged() throws IOException {
    String mapWithArgumentRemovalInformation =
        StringUtils.join(
            "\n",
            "android.constraint.Placeholder -> a.b.b.f:",
            "# {'id':'methodSignatureChanged',"
                + "'signature':["
                + "'void','updatePreLayout','android.constraint.Layout','String','float'],"
                + "'returnType': 'void',"
                + "'receiver': true,"
                + "'params':[[1,int],[2,Foo]]}",
            "# {'id':'methodSignatureChanged',"
                + "'signature':["
                + "'void','updatePreMeasure','android.constraint.Layout','String','int'],"
                + "'returnType': 'void', "
                + "'receiver': true, "
                + "'params':[[2,com.baz.Bar],[3]]}",
            "  147:161:void updatePreLayout(android.constraint.Layout,String,float) -> a",
            "  162:173:void updatePreMeasure(android.constraint.Layout,String,int) -> a",
            "  194:204:void updatePostMeasure(android.constraint.Layout,String,int) -> a");
    ClassNameMapper cnm = ClassNameMapper.mapperFromString(mapWithArgumentRemovalInformation);
    ClassNamingForNameMapper classNaming = cnm.getClassNaming("a.b.b.f");
    assertNotNull(classNaming);

    List<MemberNaming> members = classNaming.lookupByOriginalName("updatePreLayout");
    assertFalse(members.isEmpty());
    MemberNaming updatePreLayout = members.get(0);
    assertNotNull(updatePreLayout);
    assertTrue(updatePreLayout.isMethodNaming());
    MethodSignature renamedPreLayout = (MethodSignature) updatePreLayout.getRenamedSignature();
    assertEquals(3, renamedPreLayout.parameters.length);
    assertEquals("int", renamedPreLayout.parameters[0]);
    assertEquals("Foo", renamedPreLayout.parameters[1]);
    assertEquals("float", renamedPreLayout.parameters[2]);

    members = classNaming.lookupByOriginalName("updatePreMeasure");
    assertFalse(members.isEmpty());
    MemberNaming updatePreMeasure = members.get(0);
    assertNotNull(updatePreMeasure);
    assertTrue(updatePreMeasure.isMethodNaming());
    MethodSignature renamedPreMeasure = (MethodSignature) updatePreMeasure.getRenamedSignature();
    assertEquals(2, renamedPreMeasure.parameters.length);
    assertEquals("android.constraint.Layout", renamedPreMeasure.parameters[0]);
    assertEquals("com.baz.Bar", renamedPreMeasure.parameters[1]);

    members = classNaming.lookupByOriginalName("updatePostMeasure");
    assertFalse(members.isEmpty());
    MemberNaming updatePostMeasure = members.get(0);
    assertNotNull(updatePostMeasure);
    assertTrue(updatePostMeasure.isMethodNaming());
    MethodSignature renamedPostMeasure = (MethodSignature) updatePostMeasure.getRenamedSignature();
    assertEquals(3, renamedPostMeasure.parameters.length);
  }
}
