// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;
import static com.android.tools.r8.utils.DescriptorUtils.isValidJavaType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.ConstStringInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Sets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IdentifierMinifierTest extends TestBase {

  private final TestParameters parameters;
  private final String appFileName;
  private final List<String> keepRulesFiles;
  private final BiConsumer<TestParameters, CodeInspector> inspection;
  private final String test;

  public IdentifierMinifierTest(
      TestParameters parameters,
      String test,
      List<String> keepRulesFiles,
      BiConsumer<TestParameters, CodeInspector> inspection) {
    this.parameters = parameters;
    this.appFileName = ToolHelper.EXAMPLES_BUILD_DIR + test + FileUtils.JAR_EXTENSION;
    this.keepRulesFiles = keepRulesFiles;
    this.inspection = inspection;
    this.test = test;
  }

  @Test
  public void identiferMinifierTest() throws Exception {
    boolean hasWarning =
        test.equals("identifiernamestring") && keepRulesFiles.get(0).endsWith("keep-rules-2.txt");
    CodeInspector codeInspector =
        testForR8(parameters.getBackend())
            .addProgramFiles(Paths.get(appFileName))
            .addKeepRuleFiles(ListUtils.map(keepRulesFiles, Paths::get))
            .allowDiagnosticWarningMessages(hasWarning)
            .enableProguardTestOptions()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .assertAllWarningMessagesMatch(
                containsString("Cannot determine what identifier string flows to"))
            .inspector();
    inspection.accept(parameters, codeInspector);
  }

  @Parameters(name = "{0} test: {1} keep: {2}")
  public static Collection<Object[]> data() {
    List<String> tests = Arrays.asList(
        "adaptclassstrings",
        "atomicfieldupdater",
        "forname",
        "getmembers",
        "identifiernamestring");

    Map<String, BiConsumer<TestParameters, CodeInspector>> inspections = new HashMap<>();
    inspections.put("adaptclassstrings:keep-rules-1.txt", IdentifierMinifierTest::test1_rule1);
    inspections.put("adaptclassstrings:keep-rules-2.txt", IdentifierMinifierTest::test1_rule2);
    inspections.put("adaptclassstrings:keep-rules-3.txt", IdentifierMinifierTest::test1_rule3);
    inspections.put(
        "atomicfieldupdater:keep-rules.txt", IdentifierMinifierTest::test_atomicfieldupdater);
    inspections.put("forname:keep-rules.txt", IdentifierMinifierTest::test_forname);
    inspections.put("getmembers:keep-rules.txt", IdentifierMinifierTest::test_getmembers);
    inspections.put("identifiernamestring:keep-rules-1.txt", IdentifierMinifierTest::test2_rule1);
    inspections.put("identifiernamestring:keep-rules-2.txt", IdentifierMinifierTest::test2_rule2);
    inspections.put("identifiernamestring:keep-rules-3.txt", IdentifierMinifierTest::test2_rule3);
    Collection<Object[]> parameters = NamingTestBase.createTests(tests, inspections);

    List<Object[]> parametersWithBackend = new ArrayList<>();
    for (TestParameters testParameter : getTestParameters().withAllRuntimesAndApiLevels().build()) {
      for (Object[] row : parameters) {
        Object[] newRow = new Object[row.length + 1];
        newRow[0] = testParameter;
        System.arraycopy(row, 0, newRow, 1, row.length);
        parametersWithBackend.add(newRow);
      }
    }

    return parametersWithBackend;
  }

  // Without -adaptclassstrings
  private static void test1_rule1(TestParameters parameters, CodeInspector inspector) {
    int expectedRenamedIdentifierInMain = parameters.isCfRuntime() ? 0 : 0;
    test1_rules(inspector, expectedRenamedIdentifierInMain, 0, 0);
  }

  // With -adaptclassstrings *.*A
  private static void test1_rule2(TestParameters parameters, CodeInspector inspector) {
    int expectedRenamedIdentifierInMain = parameters.isCfRuntime() ? 0 : 0;
    test1_rules(inspector, expectedRenamedIdentifierInMain, 1, 1);
  }

  // With -adaptclassstrings (no filter)
  private static void test1_rule3(TestParameters parameters, CodeInspector inspector) {
    int expectedRenamedIdentifierInMain = parameters.isCfRuntime() ? 1 : 1;
    test1_rules(inspector, expectedRenamedIdentifierInMain, 1, 1);
  }

  private static void test1_rules(
      CodeInspector inspector, int countInMain, int countInABar, int countInAFields) {
    ClassSubject mainClass = inspector.clazz("adaptclassstrings.Main");
    MethodSubject main = mainClass.mainMethod();
    assertThat(main, isPresent());
    verifyPresenceOfConstString(main);
    int renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, main);
    assertEquals(countInMain, renamedYetFoundIdentifierCount);

    ClassSubject aClass = inspector.clazz("adaptclassstrings.A");
    MethodSubject bar = aClass.uniqueMethodWithName("bar");
    assertThat(bar, isPresent());
    verifyPresenceOfConstString(bar);
    renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, bar);
    assertEquals(countInABar, renamedYetFoundIdentifierCount);

    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, aClass.getDexProgramClass().staticFields());
    assertEquals(countInAFields, renamedYetFoundIdentifierCount);
  }

  private static void test_atomicfieldupdater(TestParameters parameters, CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("atomicfieldupdater.Main");
    MethodSubject main = mainClass.mainMethod();
    assertThat(main, isPresent());
    verifyPresenceOfConstString(main);

    ClassSubject a = inspector.clazz("atomicfieldupdater.A");
    Set<InstructionSubject> constStringInstructions =
        getRenamedMemberIdentifierConstStrings(a, main);
    assertEquals(3, constStringInstructions.size());
  }

  private static void test_forname(TestParameters parameters, CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("forname.Main");
    MethodSubject main = mainClass.mainMethod();
    assertThat(main, isPresent());
    verifyPresenceOfConstString(main);
    int renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, main);
    assertEquals(1, renamedYetFoundIdentifierCount);
  }

  private static void test_getmembers(TestParameters parameters, CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("getmembers.Main");
    MethodSubject main = mainClass.mainMethod();
    assertThat(main, isPresent());
    verifyPresenceOfConstString(main);

    ClassSubject a = inspector.clazz("getmembers.A");
    Set<InstructionSubject> constStringInstructions =
        getRenamedMemberIdentifierConstStrings(a, main);
    assertEquals(2, constStringInstructions.size());

    ClassSubject b = inspector.clazz("getmembers.B");
    MethodSubject inliner = b.uniqueMethodWithName("inliner");
    assertThat(inliner, isPresent());
    constStringInstructions = getRenamedMemberIdentifierConstStrings(a, inliner);
    assertEquals(1, constStringInstructions.size());
  }

  // Without -identifiernamestring
  private static void test2_rule1(TestParameters parameters, CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("identifiernamestring.Main");
    MethodSubject main = mainClass.mainMethod();
    assertThat(main, isPresent());
    verifyPresenceOfConstString(main);
    int renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, main);
    assertEquals(1, renamedYetFoundIdentifierCount);

    ClassSubject aClass = inspector.clazz("identifiernamestring.A");
    MethodSubject aInit = aClass.init();
    assertThat(aInit, isPresent());
    verifyPresenceOfConstString(aInit);
    renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, aInit);
    assertEquals(0, renamedYetFoundIdentifierCount);

    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, aClass.getDexProgramClass().staticFields());
    assertEquals(0, renamedYetFoundIdentifierCount);
  }

  // With -identifiernamestring for annotations and name-based filters
  private static void test2_rule2(TestParameters parameters, CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("identifiernamestring.Main");
    MethodSubject main = mainClass.mainMethod();
    assertThat(main, isPresent());
    verifyPresenceOfConstString(main);
    int renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, main);
    assertEquals(parameters.isCfRuntime() ? 2 : 1, renamedYetFoundIdentifierCount);

    ClassSubject aClass = inspector.clazz("identifiernamestring.A");
    MethodSubject aInit = aClass.init();
    assertThat(aInit, isPresent());
    verifyPresenceOfConstString(aInit);
    renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, aInit);
    assertEquals(1, renamedYetFoundIdentifierCount);

    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, aClass.getDexProgramClass().staticFields());
    assertEquals(2, renamedYetFoundIdentifierCount);
  }

  // With -identifiernamestring for reflective methods in testing class R.
  private static void test2_rule3(TestParameters parameters, CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("identifiernamestring.Main");
    MethodSubject main = mainClass.mainMethod();
    assertThat(main, isPresent());
    verifyPresenceOfConstString(main);

    ClassSubject b = inspector.clazz("identifiernamestring.B");
    Set<InstructionSubject> constStringInstructions =
        getRenamedMemberIdentifierConstStrings(b, main);
    assertEquals(2, constStringInstructions.size());
  }

  private static void verifyPresenceOfConstString(MethodSubject method) {
    assertTrue(
        method
            .iterateInstructions(instruction -> instruction.isConstString(JumboStringMode.ALLOW))
            .hasNext());
  }

  private static Stream<InstructionSubject> getConstStringInstructions(MethodSubject method) {
    return method.streamInstructions()
        .filter(instr -> instr.isConstString(JumboStringMode.ALLOW));
  }

  private static int countRenamedClassIdentifier(
      CodeInspector inspector, MethodSubject method) {
    return getConstStringInstructions(method)
        .reduce(
            0,
            (cnt, instr) -> {
              assert (instr instanceof ConstStringInstructionSubject);
              String cnstString =
                  ((ConstStringInstructionSubject) instr).getString().toSourceString();
              if (isValidJavaType(cnstString)) {
                ClassSubject classSubject = inspector.clazz(cnstString);
                if (classSubject.isPresent()
                    && classSubject.isRenamed()
                    && descriptorToJavaType(classSubject.getFinalDescriptor()).equals(cnstString)) {
                  return cnt + 1;
                }
              }
              return cnt;
            },
            Integer::sum);
  }

  private static int countRenamedClassIdentifier(
      CodeInspector inspector, List<DexEncodedField> fields) {
    return fields.stream()
        .filter(encodedField -> encodedField.getStaticValue().isDexValueString())
        .reduce(
            0,
            (cnt, encodedField) -> {
              String cnstString =
                  encodedField.getStaticValue().asDexValueString().getValue().toString();
              if (isValidJavaType(cnstString)) {
                ClassSubject classSubject = inspector.clazz(cnstString);
                if (classSubject.isRenamed()
                    && descriptorToJavaType(classSubject.getFinalDescriptor()).equals(cnstString)) {
                  return cnt + 1;
                }
              }
              return cnt;
            },
            Integer::sum);
  }

  private static Set<InstructionSubject> getRenamedMemberIdentifierConstStrings(
      ClassSubject clazz, MethodSubject method) {
    Set<InstructionSubject> result = Sets.newIdentityHashSet();
    getConstStringInstructions(method)
        .forEach(
            instr -> {
              String cnstString =
                  ((ConstStringInstructionSubject) instr).getString().toSourceString();
              clazz.forAllMethods(
                  foundMethodSubject -> {
                    if (foundMethodSubject.getFinalSignature().name.equals(cnstString)) {
                      result.add(instr);
                    }
                  });
              clazz.forAllFields(
                  foundFieldSubject -> {
                    if (foundFieldSubject.getFinalSignature().name.equals(cnstString)) {
                      result.add(instr);
                    }
                  });
            });
    return result;
  }
}
