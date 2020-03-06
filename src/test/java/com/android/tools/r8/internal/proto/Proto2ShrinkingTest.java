// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.analysis.ProtoApplicationStats;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Proto2ShrinkingTest extends ProtoShrinkingTestBase {

  private static final String CONTAINS_FLAGGED_OFF_FIELD =
      "com.android.tools.r8.proto2.Shrinking$ContainsFlaggedOffField";
  private static final String EXT_B =
      "com.android.tools.r8.proto2.Shrinking$PartiallyUsedWithExtension$ExtB";
  private static final String EXT_C =
      "com.android.tools.r8.proto2.Shrinking$PartiallyUsedWithExtension$ExtC";
  private static final String FLAGGED_OFF_EXTENSION =
      "com.android.tools.r8.proto2.Shrinking$HasFlaggedOffExtension$Ext";
  private static final String HAS_NO_USED_EXTENSIONS =
      "com.android.tools.r8.proto2.Shrinking$HasNoUsedExtensions";
  private static final String HAS_REQUIRED_FIELD =
      "com.android.tools.r8.proto2.Graph$HasRequiredField";
  private static final String PARTIALLY_USED =
      "com.android.tools.r8.proto2.Shrinking$PartiallyUsed";
  private static final String USED_ROOT = "com.android.tools.r8.proto2.Graph$UsedRoot";
  private static final String USED_VIA_HAZZER =
      "com.android.tools.r8.proto2.Shrinking$UsedViaHazzer";
  private static final String USES_ONLY_REPEATED_FIELDS =
      "com.android.tools.r8.proto2.Shrinking$UsesOnlyRepeatedFields";

  private static List<Path> PROGRAM_FILES =
      ImmutableList.of(PROTO2_EXAMPLES_JAR, PROTO2_PROTO_JAR, PROTOBUF_LITE_JAR);

  private final boolean allowAccessModification;
  private final boolean enableMinification;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{2}, allow access modification: {0}, enable minification: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public Proto2ShrinkingTest(
      boolean allowAccessModification, boolean enableMinification, TestParameters parameters) {
    this.allowAccessModification = allowAccessModification;
    this.enableMinification = enableMinification;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    CodeInspector inputInspector = new CodeInspector(PROGRAM_FILES);
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(PROGRAM_FILES)
            .addKeepMainRule("proto2.TestClass")
            .addKeepRuleFiles(PROTOBUF_LITE_PROGUARD_RULES)
            .addKeepRules(allGeneratedMessageLiteSubtypesAreInstantiatedRule())
            .allowAccessModification(allowAccessModification)
            .allowDiagnosticMessages()
            .allowUnusedProguardConfigurationRules()
            .enableProtoShrinking()
            .minification(enableMinification)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .assertAllInfoMessagesMatch(
                containsString("Proguard configuration rule does not match anything"))
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .inspect(
                outputInspector -> {
                  verifyMapAndRequiredFieldsAreKept(inputInspector, outputInspector);
                  verifyUnusedExtensionsAreRemoved(inputInspector, outputInspector);
                  verifyUnusedFieldsAreRemoved(inputInspector, outputInspector);
                  verifyUnusedHazzerBitFieldsAreRemoved(inputInspector, outputInspector);
                  verifyUnusedTypesAreRemoved(inputInspector, outputInspector);
                })
            .run(parameters.getRuntime(), "proto2.TestClass")
            .assertSuccessWithOutputLines(
                "--- roundtrip ---",
                "true",
                "123",
                "asdf",
                "9223372036854775807",
                "qwerty",
                "--- partiallyUsed_proto2 ---",
                "true",
                "42",
                "--- usedViaHazzer ---",
                "true",
                "--- usedViaOneofCase ---",
                "true",
                "--- usesOnlyRepeatedFields ---",
                "1",
                "--- containsFlaggedOffField ---",
                "0",
                "--- hasFlaggedOffExtension ---",
                "4",
                "--- useOneExtension ---",
                "42",
                "--- keepMapAndRequiredFields ---",
                "true",
                "10",
                "10",
                "10");

    DexItemFactory dexItemFactory = new DexItemFactory();
    ProtoApplicationStats original = new ProtoApplicationStats(dexItemFactory, inputInspector);
    ProtoApplicationStats actual =
        new ProtoApplicationStats(dexItemFactory, result.inspector(), original);

    assertEquals(
        ImmutableSet.of(),
        actual.getGeneratedExtensionRegistryStats().getSpuriouslyRetainedExtensionFields());

    if (ToolHelper.isLocalDevelopment()) {
      System.out.println(actual.getStats());
    }
  }

  private void verifyMapAndRequiredFieldsAreKept(
      CodeInspector inputInspector, CodeInspector outputInspector) {
    // Verify the existence of various fields in the input.
    {
      ClassSubject usedRootClassSubject = inputInspector.clazz(USED_ROOT);
      assertThat(usedRootClassSubject, isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithName("hasRequiredFieldA_"), isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithName("hasRequiredFieldB_"), isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithName("myOneof_"), isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithName("recursiveWithRequiredField_"), isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithName("isExtendedWithOptional_"), isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithName("isExtendedWithScalars_"), isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithName("isExtendedWithRequiredField_"), isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithName("isRepeatedlyExtendedWithRequiredField_"),
          isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithName("hasMapField_"), isPresent());

      ClassSubject hasRequiredFieldClassSubject = inputInspector.clazz(HAS_REQUIRED_FIELD);
      assertThat(hasRequiredFieldClassSubject, isPresent());
      assertThat(hasRequiredFieldClassSubject.uniqueFieldWithName("value_"), isPresent());
    }

    // Verify the existence of various fields in the output.
    {
      ClassSubject usedRootClassSubject = outputInspector.clazz(USED_ROOT);
      assertThat(usedRootClassSubject, isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithName("hasRequiredFieldA_"), isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithName("hasRequiredFieldB_"), isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithName("myOneof_"), isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithName("recursiveWithRequiredField_"), isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithName("hasMapField_"), isPresent());

      assertThat(
          usedRootClassSubject.uniqueFieldWithName("isExtendedWithRequiredField_"), isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithName("isRepeatedlyExtendedWithRequiredField_"),
          isPresent());

      ClassSubject hasRequiredFieldClassSubject = outputInspector.clazz(HAS_REQUIRED_FIELD);
      assertThat(hasRequiredFieldClassSubject, isPresent());
      assertThat(hasRequiredFieldClassSubject.uniqueFieldWithName("value_"), isPresent());
    }

    // Verify the absence of various fields in the output.
    {
      ClassSubject usedRootClassSubject = outputInspector.clazz(USED_ROOT);
      assertThat(usedRootClassSubject, isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithName("isExtendedWithOptional_"), not(isPresent()));
      assertThat(
          usedRootClassSubject.uniqueFieldWithName("isExtendedWithScalars_"), not(isPresent()));
    }
  }

  private void verifyUnusedExtensionsAreRemoved(
      CodeInspector inputInspector, CodeInspector outputInspector) {
    // Verify that the registry was split across multiple methods in the input.
    {
      ClassSubject generatedExtensionRegistryLoader =
          inputInspector.clazz("com.google.protobuf.proto2_registryGeneratedExtensionRegistryLite");
      assertThat(generatedExtensionRegistryLoader, isPresent());
      assertThat(
          generatedExtensionRegistryLoader.uniqueMethodWithName("findLiteExtensionByNumber"),
          isPresent());
      assertThat(
          generatedExtensionRegistryLoader.uniqueMethodWithName("findLiteExtensionByNumber1"),
          isPresent());
      assertThat(
          generatedExtensionRegistryLoader.uniqueMethodWithName("findLiteExtensionByNumber2"),
          isPresent());
    }

    // Verify that the registry methods are still present in the output.
    // TODO(b/112437944): Should they be optimized into a single findLiteExtensionByNumber() method?
    {
      ClassSubject generatedExtensionRegistryLoader =
          outputInspector.clazz(
              "com.google.protobuf.proto2_registryGeneratedExtensionRegistryLite");
      assertThat(generatedExtensionRegistryLoader, isPresent());
      assertThat(
          generatedExtensionRegistryLoader.uniqueMethodWithName("findLiteExtensionByNumber"),
          isPresent());
      assertThat(
          generatedExtensionRegistryLoader.uniqueMethodWithName("findLiteExtensionByNumber1"),
          isPresent());
      assertThat(
          generatedExtensionRegistryLoader.uniqueMethodWithName("findLiteExtensionByNumber2"),
          isPresent());
    }

    // Verify that unused extensions have been removed with -allowaccessmodification.
    if (allowAccessModification) {
      List<String> unusedExtensionNames =
          ImmutableList.of(FLAGGED_OFF_EXTENSION, HAS_NO_USED_EXTENSIONS, EXT_B, EXT_C);
      for (String unusedExtensionName : unusedExtensionNames) {
        assertThat(inputInspector.clazz(unusedExtensionName), isPresent());
        assertThat(
            unusedExtensionName, outputInspector.clazz(unusedExtensionName), not(isPresent()));
      }
    }
  }

  private void verifyUnusedFieldsAreRemoved(
      CodeInspector inputInspector, CodeInspector outputInspector) {
    // Verify that various proto fields are present the input.
    {
      ClassSubject cfofClassSubject = inputInspector.clazz(CONTAINS_FLAGGED_OFF_FIELD);
      assertThat(cfofClassSubject, isPresent());
      assertThat(cfofClassSubject.uniqueFieldWithName("conditionallyUsed_"), isPresent());

      ClassSubject puClassSubject = inputInspector.clazz(PARTIALLY_USED);
      assertThat(puClassSubject, isPresent());
      assertEquals(7, puClassSubject.allInstanceFields().size());
      assertThat(puClassSubject.uniqueFieldWithName("bitField0_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithName("used_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithName("completelyUnused_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithName("unusedEnum_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithName("unusedRepeatedEnum_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithName("unusedMessage_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithName("unusedRepeatedMessage_"), isPresent());

      ClassSubject uvhClassSubject = inputInspector.clazz(USED_VIA_HAZZER);
      assertThat(uvhClassSubject, isPresent());
      assertThat(uvhClassSubject.uniqueFieldWithName("used_"), isPresent());
      assertThat(uvhClassSubject.uniqueFieldWithName("unused_"), isPresent());
    }

    // Verify that various proto fields have been removed in the output.
    {
      ClassSubject cfofClassSubject = outputInspector.clazz(CONTAINS_FLAGGED_OFF_FIELD);
      assertThat(cfofClassSubject, isPresent());
      assertThat(cfofClassSubject.uniqueFieldWithName("conditionallyUsed_"), not(isPresent()));

      ClassSubject puClassSubject = outputInspector.clazz(PARTIALLY_USED);
      assertThat(puClassSubject, isPresent());
      assertThat(puClassSubject.uniqueFieldWithName("bitField0_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithName("used_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithName("completelyUnused_"), not(isPresent()));
      assertThat(puClassSubject.uniqueFieldWithName("unusedEnum_"), not(isPresent()));
      assertThat(puClassSubject.uniqueFieldWithName("unusedRepeatedEnum_"), not(isPresent()));
      assertThat(puClassSubject.uniqueFieldWithName("unusedMessage_"), not(isPresent()));
      assertThat(puClassSubject.uniqueFieldWithName("unusedRepeatedMessage_"), not(isPresent()));

      ClassSubject uvhClassSubject = outputInspector.clazz(USED_VIA_HAZZER);
      assertThat(uvhClassSubject, isPresent());
      assertThat(uvhClassSubject.uniqueFieldWithName("used_"), isPresent());
      assertThat(uvhClassSubject.uniqueFieldWithName("unused_"), not(isPresent()));
    }
  }

  private void verifyUnusedHazzerBitFieldsAreRemoved(
      CodeInspector inputInspector, CodeInspector outputInspector) {
    // Verify that various proto fields are present the input.
    {
      ClassSubject classSubject = inputInspector.clazz(USES_ONLY_REPEATED_FIELDS);
      assertThat(classSubject, isPresent());
      assertThat(classSubject.uniqueFieldWithName("bitField0_"), isPresent());
      assertThat(classSubject.uniqueFieldWithName("myoneof_"), isPresent());
      assertThat(classSubject.uniqueFieldWithName("myoneofCase_"), isPresent());
    }

    // Verify that various proto fields have been removed in the output.
    {
      ClassSubject classSubject = outputInspector.clazz(USES_ONLY_REPEATED_FIELDS);
      assertThat(classSubject, isPresent());
      assertThat(classSubject.uniqueFieldWithName("bitField0_"), not(isPresent()));
      assertThat(classSubject.uniqueFieldWithName("myoneof_"), not(isPresent()));
      assertThat(classSubject.uniqueFieldWithName("myoneofCase_"), not(isPresent()));
    }
  }

  private void verifyUnusedTypesAreRemoved(
      CodeInspector inputInspector, CodeInspector outputInspector) {
    // Verify that various types are present the input.
    {
      ClassSubject enumClassSubject = inputInspector.clazz(PARTIALLY_USED + "$Enum");
      assertThat(enumClassSubject, isPresent());

      ClassSubject nestedClassSubject = inputInspector.clazz(PARTIALLY_USED + "$Nested");
      assertThat(nestedClassSubject, isPresent());
    }

    // Verify that various types have been removed in the output.
    {
      ClassSubject enumClassSubject = outputInspector.clazz(PARTIALLY_USED + "$Enum");
      assertThat(enumClassSubject, not(isPresent()));

      ClassSubject nestedClassSubject = outputInspector.clazz(PARTIALLY_USED + "$Nested");
      assertThat(nestedClassSubject, not(isPresent()));
    }
  }

  @Test
  public void testNoRewriting() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(PROGRAM_FILES)
        .addKeepMainRule("proto2.TestClass")
        .addKeepRuleFiles(PROTOBUF_LITE_PROGUARD_RULES)
        // Retain all protos.
        .addKeepRules(keepAllProtosRule())
        // Retain the signature of dynamicMethod() and newMessageInfo().
        .addKeepRules(keepDynamicMethodSignatureRule(), keepNewMessageInfoSignatureRule())
        .allowAccessModification(allowAccessModification)
        .allowDiagnosticMessages()
        .allowUnusedProguardConfigurationRules()
        .enableProtoShrinking()
        .minification(enableMinification)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertAllInfoMessagesMatch(
            containsString("Proguard configuration rule does not match anything"))
        .assertAllWarningMessagesMatch(
            anyOf(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."),
                containsString("required for default or static interface methods desugaring")))
        .inspect(
            inspector ->
                assertRewrittenProtoSchemasMatch(new CodeInspector(PROGRAM_FILES), inspector));
  }
}
