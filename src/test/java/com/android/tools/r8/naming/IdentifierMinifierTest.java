// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;
import static com.android.tools.r8.utils.DescriptorUtils.isValidJavaType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.ConstStringInstructionSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IdentifierMinifierTest extends TestBase {

  private final String appFileName;
  private final List<String> keepRulesFiles;
  private final Consumer<CodeInspector> inspection;
  private final Backend backend;

  public IdentifierMinifierTest(
      Backend backend,
      String test,
      List<String> keepRulesFiles,
      Consumer<CodeInspector> inspection) {
    assert backend == Backend.DEX || backend == Backend.CF;
    this.appFileName =
        ToolHelper.EXAMPLES_BUILD_DIR + test + (backend == Backend.DEX ? "/classes.dex" : ".jar");
    this.keepRulesFiles = keepRulesFiles;
    this.inspection = inspection;
    this.backend = backend;
  }

  private AndroidApp processedApp;

  @Before
  public void generateR8ProcessedApp() throws Exception {
    Path out = temp.getRoot().toPath();
    R8Command.Builder builder =
        ToolHelper.addProguardConfigurationConsumer(
                R8Command.builder(),
                pgConfig -> {
                  pgConfig.setPrintMapping(true);
                  pgConfig.setPrintMappingFile(out.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE));
                })
            .setOutput(out, backend == Backend.DEX ? OutputMode.DexIndexed : OutputMode.ClassFile)
            .addProguardConfigurationFiles(ListUtils.map(keepRulesFiles, Paths::get));
    if (backend == Backend.DEX) {
      builder.addLibraryFiles(ToolHelper.getDefaultAndroidJar());
    } else if (backend == Backend.CF) {
      builder.addLibraryFiles(ToolHelper.getJava8RuntimeJar());
    }
    ToolHelper.getAppBuilder(builder).addProgramFiles(Paths.get(appFileName));
    processedApp = ToolHelper.runR8(builder.build(), o -> o.debug = false);
  }

  @Test
  public void identiferMinifierTest() throws Exception {
    CodeInspector codeInspector = new CodeInspector(processedApp);
    inspection.accept(codeInspector);
  }

  @Parameters(name = "[{0}] test: {1} keep: {2}")
  public static Collection<Object[]> data() {
    List<String> tests = Arrays.asList(
        "adaptclassstrings",
        "atomicfieldupdater",
        "forname",
        "getmembers",
        "identifiernamestring");

    Map<String, Consumer<CodeInspector>> inspections = new HashMap<>();
    inspections.put("adaptclassstrings:keep-rules-1.txt", IdentifierMinifierTest::test1_rule1);
    inspections.put("adaptclassstrings:keep-rules-2.txt", IdentifierMinifierTest::test1_rule2);
    inspections.put(
        "atomicfieldupdater:keep-rules.txt", IdentifierMinifierTest::test_atomicfieldupdater);
    inspections.put("forname:keep-rules.txt", IdentifierMinifierTest::test_forname);
    inspections.put("getmembers:keep-rules.txt", IdentifierMinifierTest::test_getmembers);
    inspections.put("identifiernamestring:keep-rules-1.txt", IdentifierMinifierTest::test2_rule1);
    inspections.put("identifiernamestring:keep-rules-2.txt", IdentifierMinifierTest::test2_rule2);
    inspections.put("identifiernamestring:keep-rules-3.txt", IdentifierMinifierTest::test2_rule3);

    Collection<Object[]> parameters = NamingTestBase.createTests(tests, inspections);

    // Duplicate parameters for each backend.
    List<Object[]> parametersWithBackend = new ArrayList<>();
    for (Backend backend : Backend.values()) {
      for (Object[] row : parameters) {
        Object[] newRow = new Object[row.length + 1];
        newRow[0] = backend;
        System.arraycopy(row, 0, newRow, 1, row.length);
        parametersWithBackend.add(newRow);
      }
    }

    return parametersWithBackend;
  }

  // Without -adaptclassstrings
  private static void test1_rule1(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("adaptclassstrings.Main");
    MethodSubject main = mainClass.method(CodeInspector.MAIN);
    assertTrue(main instanceof FoundMethodSubject);
    FoundMethodSubject foundMain = (FoundMethodSubject) main;
    verifyPresenceOfConstString(foundMain);
    int renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, foundMain);
    assertEquals(4, renamedYetFoundIdentifierCount);

    ClassSubject aClass = inspector.clazz("adaptclassstrings.A");
    MethodSubject bar = aClass.method("void", "bar", ImmutableList.of());
    assertTrue(bar instanceof FoundMethodSubject);
    FoundMethodSubject foundBar = (FoundMethodSubject) bar;
    verifyPresenceOfConstString(foundBar);
    renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, foundBar);
    assertEquals(0, renamedYetFoundIdentifierCount);

    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, aClass.getDexClass().staticFields());
    assertEquals(0, renamedYetFoundIdentifierCount);
  }

  // With -adaptclassstrings *.*A
  private static void test1_rule2(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("adaptclassstrings.Main");
    MethodSubject main = mainClass.method(CodeInspector.MAIN);
    assertTrue(main instanceof FoundMethodSubject);
    FoundMethodSubject foundMain = (FoundMethodSubject) main;
    verifyPresenceOfConstString(foundMain);
    int renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, foundMain);
    assertEquals(4, renamedYetFoundIdentifierCount);

    ClassSubject aClass = inspector.clazz("adaptclassstrings.A");
    MethodSubject bar = aClass.method("void", "bar", ImmutableList.of());
    assertTrue(bar instanceof FoundMethodSubject);
    FoundMethodSubject foundBar = (FoundMethodSubject) bar;
    verifyPresenceOfConstString(foundBar);
    renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, foundBar);
    assertEquals(1, renamedYetFoundIdentifierCount);

    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, aClass.getDexClass().staticFields());
    assertEquals(1, renamedYetFoundIdentifierCount);
  }

  private static void test_atomicfieldupdater(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("atomicfieldupdater.Main");
    MethodSubject main = mainClass.method(CodeInspector.MAIN);
    assertTrue(main instanceof FoundMethodSubject);
    FoundMethodSubject foundMain = (FoundMethodSubject) main;
    verifyPresenceOfConstString(foundMain);

    ClassSubject a = inspector.clazz("atomicfieldupdater.A");
    Set<InstructionSubject> constStringInstructions =
        getRenamedMemberIdentifierConstStrings(a, foundMain);
    assertEquals(3, constStringInstructions.size());
  }

  private static void test_forname(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("forname.Main");
    MethodSubject main = mainClass.method(CodeInspector.MAIN);
    assertTrue(main instanceof FoundMethodSubject);
    FoundMethodSubject foundMain = (FoundMethodSubject) main;
    verifyPresenceOfConstString(foundMain);
    int renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, foundMain);
    assertEquals(1, renamedYetFoundIdentifierCount);
  }

  private static void test_getmembers(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("getmembers.Main");
    MethodSubject main = mainClass.method(CodeInspector.MAIN);
    assertTrue(main instanceof FoundMethodSubject);
    FoundMethodSubject foundMain = (FoundMethodSubject) main;
    verifyPresenceOfConstString(foundMain);

    ClassSubject a = inspector.clazz("getmembers.A");
    Set<InstructionSubject> constStringInstructions =
        getRenamedMemberIdentifierConstStrings(a, foundMain);
    assertEquals(2, constStringInstructions.size());

    ClassSubject b = inspector.clazz("getmembers.B");
    MethodSubject inliner = b.method("java.lang.String", "inliner", ImmutableList.of());
    assertTrue(inliner instanceof FoundMethodSubject);
    FoundMethodSubject foundInliner = (FoundMethodSubject) inliner;
    constStringInstructions = getRenamedMemberIdentifierConstStrings(a, foundInliner);
    assertEquals(1, constStringInstructions.size());
  }

  // Without -identifiernamestring
  private static void test2_rule1(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("identifiernamestring.Main");
    MethodSubject main = mainClass.method(CodeInspector.MAIN);
    assertTrue(main instanceof FoundMethodSubject);
    FoundMethodSubject foundMain = (FoundMethodSubject) main;
    verifyPresenceOfConstString(foundMain);
    int renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, foundMain);
    assertEquals(1, renamedYetFoundIdentifierCount);

    ClassSubject aClass = inspector.clazz("identifiernamestring.A");
    MethodSubject aInit =
        aClass.method("void", "<init>", ImmutableList.of());
    assertTrue(aInit instanceof FoundMethodSubject);
    FoundMethodSubject foundAInit = (FoundMethodSubject) aInit;
    verifyPresenceOfConstString(foundAInit);
    renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, foundAInit);
    assertEquals(0, renamedYetFoundIdentifierCount);

    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, aClass.getDexClass().staticFields());
    assertEquals(0, renamedYetFoundIdentifierCount);
  }

  // With -identifiernamestring for annotations and name-based filters
  private static void test2_rule2(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("identifiernamestring.Main");
    MethodSubject main = mainClass.method(CodeInspector.MAIN);
    assertTrue(main instanceof FoundMethodSubject);
    FoundMethodSubject foundMain = (FoundMethodSubject) main;
    verifyPresenceOfConstString(foundMain);
    int renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, foundMain);
    assertEquals(2, renamedYetFoundIdentifierCount);

    ClassSubject aClass = inspector.clazz("identifiernamestring.A");
    MethodSubject aInit =
        aClass.method("void", "<init>", ImmutableList.of());
    assertTrue(aInit instanceof FoundMethodSubject);
    FoundMethodSubject foundAInit = (FoundMethodSubject) aInit;
    verifyPresenceOfConstString(foundAInit);
    renamedYetFoundIdentifierCount = countRenamedClassIdentifier(inspector, foundAInit);
    assertEquals(1, renamedYetFoundIdentifierCount);

    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, aClass.getDexClass().staticFields());
    assertEquals(2, renamedYetFoundIdentifierCount);
  }

  // With -identifiernamestring for reflective methods in testing class R.
  private static void test2_rule3(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("identifiernamestring.Main");
    MethodSubject main = mainClass.method(CodeInspector.MAIN);
    assertTrue(main instanceof FoundMethodSubject);
    FoundMethodSubject foundMain = (FoundMethodSubject) main;
    verifyPresenceOfConstString(foundMain);

    ClassSubject b = inspector.clazz("identifiernamestring.B");
    Set<InstructionSubject> constStringInstructions =
        getRenamedMemberIdentifierConstStrings(b, foundMain);
    assertEquals(2, constStringInstructions.size());
  }

  private static void verifyPresenceOfConstString(FoundMethodSubject method) {
    assertTrue(
        method
            .iterateInstructions(instruction -> instruction.isConstString(JumboStringMode.ALLOW))
            .hasNext());
  }

  private static Stream<InstructionSubject> getConstStringInstructions(FoundMethodSubject method) {
    return Streams.stream(method.iterateInstructions())
        .filter(instr -> instr.isConstString(JumboStringMode.ALLOW));
  }

  private static int countRenamedClassIdentifier(
      CodeInspector inspector, FoundMethodSubject method) {
    return getConstStringInstructions(method)
        .reduce(
            0,
            (cnt, instr) -> {
              assert (instr instanceof ConstStringInstructionSubject);
              String cnstString =
                  ((ConstStringInstructionSubject) instr).getString().toSourceString();
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

  private static int countRenamedClassIdentifier(
      CodeInspector inspector, DexEncodedField[] fields) {
    return Arrays.stream(fields)
        .filter(encodedField -> encodedField.getStaticValue() instanceof DexValueString)
        .reduce(0, (cnt, encodedField) -> {
          String cnstString =
              ((DexValueString) encodedField.getStaticValue()).getValue().toString();
          if (isValidJavaType(cnstString)) {
            ClassSubject classSubject = inspector.clazz(cnstString);
            if (classSubject.isRenamed()
                && descriptorToJavaType(classSubject.getFinalDescriptor()).equals(cnstString)) {
              return cnt + 1;
            }
          }
          return cnt;
        }, Integer::sum);
  }

  private static Set<InstructionSubject> getRenamedMemberIdentifierConstStrings(
      ClassSubject clazz, FoundMethodSubject method) {
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
