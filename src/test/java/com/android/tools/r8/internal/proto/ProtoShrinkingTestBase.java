// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.ir.analysis.proto.ProtoUtils.getInfoValueFromMessageInfoConstructionInvoke;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.analysis.proto.GeneratedMessageLiteShrinker;
import com.android.tools.r8.ir.analysis.proto.ProtoReferences;
import com.android.tools.r8.ir.analysis.proto.RawMessageInfoDecoder;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public abstract class ProtoShrinkingTestBase extends TestBase {

  public static final Path PROTOBUF_LITE_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "protobuf-lite/libprotobuf_lite.jar");

  public static final Path PROTOBUF_LITE_PROGUARD_RULES =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "protobuf-lite/lite_proguard.pgcfg");

  // Test classes for proto2. Use a checked in version of the built examples.
  public static final Path PROTO2_EXAMPLES_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "proto", "examplesProto2.jar");

  // Proto definitions used by test classes for proto2.
  public static final Path PROTO2_PROTO_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "proto", "examplesGeneratedProto2.jar");

  // Test classes for proto3.
  public static final Path PROTO3_EXAMPLES_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "proto", "examplesProto3.jar");

  // Proto definitions used by test classes for proto3.
  public static final Path PROTO3_PROTO_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "proto", "examplesGeneratedProto3.jar");

  public static void assertRewrittenProtoSchemasMatch(
      CodeInspector expectedInspector, CodeInspector actualInspector) throws Exception {
    Map<String, IntList> actualInfos = getInfoValues(actualInspector);

    // Ensure that this cannot fail silently.
    assertTrue(actualInfos.size() > 0);

    Map<String, IntList> expectedInfos = getInfoValues(expectedInspector);
    for (Map.Entry<String, IntList> entry : actualInfos.entrySet()) {
      String className = entry.getKey();
      IntList actualInfo = entry.getValue();
      IntList expectedInfo = expectedInfos.get(className);
      assertNotNull("Expected info value missing for class `" + className + "`", expectedInfo);
      assertEquals("Unexpected info value for class `" + className + "`", expectedInfo, actualInfo);
    }
  }

  static String allGeneratedMessageLiteSubtypesAreInstantiatedRule() {
    return StringUtils.lines(
        "-if class * extends com.google.protobuf.GeneratedMessageLite",
        "-keep,allowobfuscation class <1> {",
        "  <init>(...);",
        "}");
  }

  static String findLiteExtensionByNumberInDuplicateCalledRule() {
    return StringUtils.lines(
        "-keep class com.google.protobuf.proto2_registryGeneratedExtensionRegistryLiteDuplicate {",
        "  findLiteExtensionByNumber(...);",
        "}");
  }

  public static String keepAllProtosRule() {
    return StringUtils.lines(
        "-if class * extends com.google.protobuf.GeneratedMessageLite",
        "-keep,allowobfuscation class <1> { <init>(...); <fields>; }");
  }

  public static String keepDynamicMethodSignatureRule() {
    return StringUtils.lines(
        "-keepclassmembers,includedescriptorclasses class com.google.protobuf.GeneratedMessageLite "
            + "{",
        "  java.lang.Object dynamicMethod(com.google.protobuf.GeneratedMessageLite$MethodToInvoke,"
            + " java.lang.Object, java.lang.Object);",
        "}");
  }

  public static String keepNewMessageInfoSignatureRule() {
    return StringUtils.lines(
        "-keepclassmembers,includedescriptorclasses class com.google.protobuf.GeneratedMessageLite "
            + "{",
        "  java.lang.Object newMessageInfo(com.google.protobuf.MessageLite,"
            + " java.lang.String, java.lang.Object[]);",
        "}");
  }

  /**
   * Finds all proto messages and creates a mapping from the type name of the message to the
   * expected info value of the message.
   */
  static Map<String, IntList> getInfoValues(CodeInspector inspector) throws Exception {
    Map<String, IntList> result = new HashMap<>();
    DexItemFactory dexItemFactory = inspector.getFactory();
    ProtoReferences references = new ProtoReferences(dexItemFactory);
    for (FoundClassSubject classSubject : inspector.allClasses()) {
      for (FoundMethodSubject methodSubject : classSubject.virtualMethods()) {
        if (!methodSubject.hasCode() || !references.isDynamicMethod(methodSubject.getMethod())) {
          continue;
        }

        IRCode code = methodSubject.buildIR();
        InvokeMethod invoke =
            GeneratedMessageLiteShrinker.getNewMessageInfoInvoke(code, references);
        assert invoke != null;

        IntList info = new IntArrayList();
        RawMessageInfoDecoder.createInfoIterator(
                getInfoValueFromMessageInfoConstructionInvoke(invoke, references))
            .forEachRemaining(info::add);
        result.put(classSubject.getOriginalName(), info);
      }
    }
    return result;
  }

  void inspectWarningMessages(R8TestCompileResult compileResult) {
    compileResult.assertAllWarningMessagesMatch(
        anyOf(
            equalTo("Resource 'META-INF/MANIFEST.MF' already exists."),
            allOf(
                startsWith(
                    "Rule matches the static final field `java.lang.String com.google."
                        + "protobuf.proto2_registryGeneratedExtensionRegistryLite."
                        + "CONTAINING_TYPE_"),
                containsString("`, which may have been inlined: -identifiernamestring"))));
  }
}
