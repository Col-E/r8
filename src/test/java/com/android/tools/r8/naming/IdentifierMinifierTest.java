// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;
import static com.android.tools.r8.utils.DescriptorUtils.isValidJavaType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.ConstStringJumbo;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OutputMode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IdentifierMinifierTest {

  private final String appFileName;
  private final List<String> keepRulesFiles;
  private final Consumer<DexInspector> inspection;

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  public IdentifierMinifierTest(
      String test,
      List<String> keepRulesFiles,
      Consumer<DexInspector> inspection) {
    this.appFileName = ToolHelper.EXAMPLES_BUILD_DIR + test + "/classes.dex";
    this.keepRulesFiles = keepRulesFiles;
    this.inspection = inspection;
  }

  @Before
  public void generateR8ProcessedApp() throws Exception {
    Path out = temp.getRoot().toPath();
    R8Command command =
        ToolHelper.addProguardConfigurationConsumer(
                R8Command.builder(),
                pgConfig -> {
                  pgConfig.setPrintMapping(true);
                  pgConfig.setPrintMappingFile(out.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE));
                })
            .setOutput(out, OutputMode.DexIndexed)
            .addProgramFiles(Paths.get(appFileName))
            .addProguardConfigurationFiles(ListUtils.map(keepRulesFiles, Paths::get))
            .addLibraryFiles(Paths.get(ToolHelper.getDefaultAndroidJar()))
            .build();
    ToolHelper.runR8(command);
  }

  @Test
  public void identiferMinifierTest() throws Exception {
    Path out = temp.getRoot().toPath();
    DexInspector dexInspector =
        new DexInspector(
            out.resolve("classes.dex"),
            out.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE).toString());
    inspection.accept(dexInspector);
  }

  @Parameters(name = "test: {0} keep: {1}")
  public static Collection<Object[]> data() {
    List<String> tests = Arrays.asList(
        "adaptclassstrings",
        "atomicfieldupdater",
        "forname",
        "getmembers",
        "identifiernamestring");

    Map<String, Consumer<DexInspector>> inspections = new HashMap<>();
    inspections.put("adaptclassstrings:keep-rules-1.txt", IdentifierMinifierTest::test1_rule1);
    inspections.put("adaptclassstrings:keep-rules-2.txt", IdentifierMinifierTest::test1_rule2);
    inspections.put(
        "atomicfieldupdater:keep-rules.txt", IdentifierMinifierTest::test_atomicfieldupdater);
    inspections.put("forname:keep-rules.txt", IdentifierMinifierTest::test_forname);
    inspections.put("getmembers:keep-rules.txt", IdentifierMinifierTest::test_getmembers);
    inspections.put("identifiernamestring:keep-rules-1.txt", IdentifierMinifierTest::test2_rule1);
    inspections.put("identifiernamestring:keep-rules-2.txt", IdentifierMinifierTest::test2_rule2);
    inspections.put("identifiernamestring:keep-rules-3.txt", IdentifierMinifierTest::test2_rule3);

    return NamingTestBase.createTests(tests, inspections);
  }

  // Without -adaptclassstrings
  private static void test1_rule1(DexInspector inspector) {
    ClassSubject mainClass = inspector.clazz("adaptclassstrings.Main");
    MethodSubject main = mainClass.method(DexInspector.MAIN);
    Code mainCode = main.getMethod().getCode();
    verifyPresenceOfConstString(mainCode.asDexCode().instructions);
    int renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, mainCode.asDexCode().instructions);
    assertEquals(0, renamedYetFoundIdentifierCount);

    ClassSubject aClass = inspector.clazz("adaptclassstrings.A");
    MethodSubject bar = aClass.method("void", "bar", ImmutableList.of());
    Code barCode = bar.getMethod().getCode();
    verifyPresenceOfConstString(barCode.asDexCode().instructions);
    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, barCode.asDexCode().instructions);
    assertEquals(0, renamedYetFoundIdentifierCount);

    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, aClass.getDexClass().staticFields());
    assertEquals(0, renamedYetFoundIdentifierCount);
  }

  // With -adaptclassstrings *.*A
  private static void test1_rule2(DexInspector inspector) {
    ClassSubject mainClass = inspector.clazz("adaptclassstrings.Main");
    MethodSubject main = mainClass.method(DexInspector.MAIN);
    Code mainCode = main.getMethod().getCode();
    verifyPresenceOfConstString(mainCode.asDexCode().instructions);
    int renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, mainCode.asDexCode().instructions);
    assertEquals(0, renamedYetFoundIdentifierCount);

    ClassSubject aClass = inspector.clazz("adaptclassstrings.A");
    MethodSubject bar = aClass.method("void", "bar", ImmutableList.of());
    Code barCode = bar.getMethod().getCode();
    verifyPresenceOfConstString(barCode.asDexCode().instructions);
    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, barCode.asDexCode().instructions);
    assertEquals(1, renamedYetFoundIdentifierCount);

    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, aClass.getDexClass().staticFields());
    assertEquals(1, renamedYetFoundIdentifierCount);
  }

  private static void test_atomicfieldupdater(DexInspector inspector) {
    ClassSubject mainClass = inspector.clazz("atomicfieldupdater.Main");
    MethodSubject main = mainClass.method(DexInspector.MAIN);
    Code mainCode = main.getMethod().getCode();
    verifyPresenceOfConstString(mainCode.asDexCode().instructions);

    ClassSubject a = inspector.clazz("atomicfieldupdater.A");
    Set<Instruction> constStringInstructions =
        getRenamedMemberIdentifierConstStrings(a, mainCode.asDexCode().instructions);
    assertEquals(3, constStringInstructions.size());
  }

  private static void test_forname(DexInspector inspector) {
    ClassSubject mainClass = inspector.clazz("forname.Main");
    MethodSubject main = mainClass.method(DexInspector.MAIN);
    Code mainCode = main.getMethod().getCode();
    verifyPresenceOfConstString(mainCode.asDexCode().instructions);
    int renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, mainCode.asDexCode().instructions);
    assertEquals(1, renamedYetFoundIdentifierCount);
  }

  private static void test_getmembers(DexInspector inspector) {
    ClassSubject mainClass = inspector.clazz("getmembers.Main");
    MethodSubject main = mainClass.method(DexInspector.MAIN);
    Code mainCode = main.getMethod().getCode();
    verifyPresenceOfConstString(mainCode.asDexCode().instructions);

    ClassSubject a = inspector.clazz("getmembers.A");
    Set<Instruction> constStringInstructions =
        getRenamedMemberIdentifierConstStrings(a, mainCode.asDexCode().instructions);
    assertEquals(2, constStringInstructions.size());
  }

  // Without -identifiernamestring
  private static void test2_rule1(DexInspector inspector) {
    ClassSubject mainClass = inspector.clazz("identifiernamestring.Main");
    MethodSubject main = mainClass.method(DexInspector.MAIN);
    Code mainCode = main.getMethod().getCode();
    verifyPresenceOfConstString(mainCode.asDexCode().instructions);
    int renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, mainCode.asDexCode().instructions);
    assertEquals(0, renamedYetFoundIdentifierCount);

    ClassSubject aClass = inspector.clazz("identifiernamestring.A");
    MethodSubject aInit =
        aClass.method("void", "<init>", ImmutableList.of());
    Code initCode = aInit.getMethod().getCode();
    verifyPresenceOfConstString(initCode.asDexCode().instructions);
    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, initCode.asDexCode().instructions);
    assertEquals(0, renamedYetFoundIdentifierCount);

    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, aClass.getDexClass().staticFields());
    assertEquals(0, renamedYetFoundIdentifierCount);
  }

  // With -identifiernamestring for annotations and name-based filters
  private static void test2_rule2(DexInspector inspector) {
    ClassSubject mainClass = inspector.clazz("identifiernamestring.Main");
    MethodSubject main = mainClass.method(DexInspector.MAIN);
    assertTrue(main.isPresent());
    Code mainCode = main.getMethod().getCode();
    assertTrue(mainCode.isDexCode());
    verifyPresenceOfConstString(mainCode.asDexCode().instructions);
    int renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, mainCode.asDexCode().instructions);
    assertEquals(1, renamedYetFoundIdentifierCount);

    ClassSubject aClass = inspector.clazz("identifiernamestring.A");
    MethodSubject aInit =
        aClass.method("void", "<init>", ImmutableList.of());
    Code initCode = aInit.getMethod().getCode();
    verifyPresenceOfConstString(initCode.asDexCode().instructions);
    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, initCode.asDexCode().instructions);
    assertEquals(1, renamedYetFoundIdentifierCount);

    renamedYetFoundIdentifierCount =
        countRenamedClassIdentifier(inspector, aClass.getDexClass().staticFields());
    assertEquals(2, renamedYetFoundIdentifierCount);
  }

  // With -identifiernamestring for reflective methods
  private static void test2_rule3(DexInspector inspector) {
    ClassSubject mainClass = inspector.clazz("identifiernamestring.Main");
    MethodSubject main = mainClass.method(DexInspector.MAIN);
    assertTrue(main.isPresent());
    Code mainCode = main.getMethod().getCode();
    assertTrue(mainCode.isDexCode());
    verifyPresenceOfConstString(mainCode.asDexCode().instructions);

    ClassSubject b = inspector.clazz("identifiernamestring.B");
    Set<Instruction> constStringInstructions =
        getRenamedMemberIdentifierConstStrings(b, mainCode.asDexCode().instructions);
    assertEquals(2, constStringInstructions.size());
  }

  private static void verifyPresenceOfConstString(Instruction[] instructions) {
    boolean presence =
        Arrays.stream(instructions)
            .anyMatch(instr -> instr instanceof ConstString || instr instanceof ConstStringJumbo);
    assertTrue(presence);
  }

  private static String retrieveString(Instruction instr) {
    if (instr instanceof ConstString) {
      ConstString cnst = (ConstString) instr;
      return cnst.getString().toString();
    } else if (instr instanceof ConstStringJumbo) {
      ConstStringJumbo cnst = (ConstStringJumbo) instr;
      return cnst.getString().toString();
    }
    return null;
  }

  private static Stream<Instruction> getConstStringInstructions(Instruction[] instructions) {
    return Arrays.stream(instructions)
        .filter(instr -> instr instanceof ConstString || instr instanceof ConstStringJumbo);
  }

  private static int countRenamedClassIdentifier(
      DexInspector inspector, Instruction[] instructions) {
    return getConstStringInstructions(instructions)
        .reduce(0, (cnt, instr) -> {
          String cnstString = retrieveString(instr);
          assertNotNull(cnstString);
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

  private static int countRenamedClassIdentifier(
      DexInspector inspector, DexEncodedField[] fields) {
    return Arrays.stream(fields)
        .filter(encodedField -> encodedField.staticValue instanceof DexValueString)
        .reduce(0, (cnt, encodedField) -> {
          String cnstString = ((DexValueString) encodedField.staticValue).getValue().toString();
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

  private static Set<Instruction> getRenamedMemberIdentifierConstStrings(
      ClassSubject clazz, Instruction[] instructions) {
    Set<Instruction> result = Sets.newIdentityHashSet();
    getConstStringInstructions(instructions).forEach(instr -> {
      String cnstString = retrieveString(instr);
      clazz.forAllMethods(foundMethodSubject -> {
        if (foundMethodSubject.getFinalSignature().name.equals(cnstString)) {
          result.add(instr);
        }
      });
      clazz.forAllFields(foundFieldSubject -> {
        if (foundFieldSubject.getFinalSignature().name.equals(cnstString)) {
          result.add(instr);
        }
      });
    });
    return result;
  }

}
