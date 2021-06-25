// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.records;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfTypeInstruction;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.desugar.records.RecordMethods.RecordStub;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.records.RecordRewriter;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.SortedMap;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateRecordMethods extends MethodGenerationBase {
  private final DexType GENERATED_TYPE =
      factory.createType("Lcom/android/tools/r8/ir/desugar/records/RecordCfMethods;");
  private final DexType RECORD_STUB_TYPE =
      factory.createType(DescriptorUtils.javaTypeToDescriptor(RecordStub.class.getTypeName()));
  private final List<Class<?>> METHOD_TEMPLATE_CLASSES = ImmutableList.of(RecordMethods.class);

  protected final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntime(CfVm.JDK9).build();
  }

  public GenerateRecordMethods(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Override
  protected DexType getGeneratedType() {
    return GENERATED_TYPE;
  }

  @Override
  protected List<Class<?>> getMethodTemplateClasses() {
    return METHOD_TEMPLATE_CLASSES;
  }

  @Override
  protected int getYear() {
    return 2021;
  }

  @Override
  protected CfCode getCode(String holderName, String methodName, CfCode code) {
    code.setInstructions(
        code.getInstructions().stream()
            .map(instruction -> rewriteRecordStub(instruction))
            .collect(Collectors.toList()));
    return code;
  }

  private CfInstruction rewriteRecordStub(CfInstruction instruction) {
    if (instruction.isTypeInstruction()) {
      CfTypeInstruction typeInstruction = instruction.asTypeInstruction();
      return typeInstruction.withType(rewriteType(typeInstruction.getType()));
    }
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      DexMethod method = cfInvoke.getMethod();
      DexMethod newMethod =
          factory.createMethod(rewriteType(method.holder), method.proto, rewriteName(method.name));
      return new CfInvoke(cfInvoke.getOpcode(), newMethod, cfInvoke.isInterface());
    }
    if (instruction.isFrame()) {
      CfFrame cfFrame = instruction.asFrame();
      return new CfFrame(
          rewriteLocals(cfFrame.getLocalsAsSortedMap()), rewriteStack(cfFrame.getStack()));
    }
    return instruction;
  }

  private String rewriteName(DexString name) {
    return name.toString().equals("getFieldsAsObjects")
        ? RecordRewriter.GET_FIELDS_AS_OBJECTS_METHOD_NAME
        : name.toString();
  }

  private DexType rewriteType(DexType type) {
    DexType baseType = type.isArrayType() ? type.toBaseType(factory) : type;
    if (baseType != RECORD_STUB_TYPE) {
      return type;
    }
    return type.isArrayType()
        ? type.replaceBaseType(factory.recordType, factory)
        : factory.recordType;
  }

  private FrameType rewriteFrameType(FrameType frameType) {
    if (frameType.isInitialized() && frameType.getInitializedType().isReferenceType()) {
      DexType newType = rewriteType(frameType.getInitializedType());
      if (newType == frameType.getInitializedType()) {
        return frameType;
      }
      return FrameType.initialized(newType);
    } else {
      assert !frameType.isUninitializedNew();
      assert !frameType.isUninitializedThis();
      return frameType;
    }
  }

  private SortedMap<Integer, FrameType> rewriteLocals(SortedMap<Integer, FrameType> locals) {
    Int2ReferenceSortedMap<FrameType> newLocals = new Int2ReferenceAVLTreeMap<>();
    locals.forEach((index, local) -> newLocals.put((int) index, rewriteFrameType(local)));
    return newLocals;
  }

  private Deque<FrameType> rewriteStack(Deque<FrameType> stack) {
    ArrayDeque<FrameType> newStack = new ArrayDeque<>();
    stack.forEach(frameType -> newStack.add(rewriteFrameType(frameType)));
    return newStack;
  }

  @Test
  public void testRecordMethodsGenerated() throws Exception {
    ArrayList<Class<?>> sorted = new ArrayList<>(getMethodTemplateClasses());
    sorted.sort(Comparator.comparing(Class::getTypeName));
    assertEquals("Classes should be listed in sorted order", sorted, getMethodTemplateClasses());
    assertEquals(
        FileUtils.readTextFile(getGeneratedFile(), StandardCharsets.UTF_8), generateMethods());
  }

  public static void main(String[] args) throws Exception {
    new GenerateRecordMethods(null).generateMethodsAndWriteThemToFile();
  }
}
