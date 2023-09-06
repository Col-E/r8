// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class IRProcessingCallGraphUseRegistry<N extends NodeBase<N>> extends InvokeExtractor<N> {

  private final FieldAccessInfoCollection<?> fieldAccessInfoCollection;

  IRProcessingCallGraphUseRegistry(
      AppView<AppInfoWithLiveness> appView,
      N currentMethod,
      Function<ProgramMethod, N> nodeFactory,
      Map<DexMethod, ProgramMethodSet> possibleProgramTargetsCache,
      Predicate<ProgramMethod> targetTester) {
    super(appView, currentMethod, nodeFactory, possibleProgramTargetsCache, targetTester);
    this.fieldAccessInfoCollection = appView.appInfo().getFieldAccessInfoCollection();
  }

  protected void addClassInitializerTarget(DexProgramClass clazz) {
    assert clazz != null;
    if (clazz.hasClassInitializer()) {
      addCallEdge(clazz.getProgramClassInitializer(), false);
    }
  }

  protected void addClassInitializerTarget(DexType type) {
    assert type.isClassType();
    DexProgramClass clazz = asProgramClassOrNull(appViewWithLiveness.definitionFor(type));
    if (clazz != null) {
      addClassInitializerTarget(clazz);
    }
  }

  private void addFieldReadEdge(ProgramMethod writer) {
    assert !writer.getDefinition().isAbstract();
    if (!targetTester.test(writer)) {
      return;
    }
    nodeFactory.apply(writer).addReaderConcurrently(currentMethod);
  }

  private void processFieldRead(DexField reference) {
    DexField rewrittenReference =
        appViewWithLiveness.graphLens().lookupField(reference, getCodeLens());
    if (!rewrittenReference.getHolderType().isClassType()) {
      return;
    }

    ProgramField field =
        appViewWithLiveness.appInfo().resolveField(rewrittenReference).getSingleProgramField();
    if (field == null || appViewWithLiveness.appInfo().isPinned(field)) {
      return;
    }

    // Each static field access implicitly triggers the class initializer.
    if (field.getAccessFlags().isStatic()) {
      addClassInitializerTarget(field.getHolder());
    }

    FieldAccessInfo fieldAccessInfo = fieldAccessInfoCollection.get(field.getReference());
    if (fieldAccessInfo != null && fieldAccessInfo.hasKnownWriteContexts()) {
      if (fieldAccessInfo.getNumberOfWriteContexts() == 1) {
        fieldAccessInfo.forEachWriteContext(this::addFieldReadEdge);
      }
    }
  }

  private void processFieldWrite(DexField reference) {
    DexField rewrittenReference =
        appViewWithLiveness.graphLens().lookupField(reference, getCodeLens());
    if (!rewrittenReference.getHolderType().isClassType()) {
      return;
    }

    ProgramField field =
        appViewWithLiveness.appInfo().resolveField(rewrittenReference).getSingleProgramField();
    if (field == null || appViewWithLiveness.appInfo().isPinned(field)) {
      return;
    }

    // Each static field access implicitly triggers the class initializer.
    if (field.getAccessFlags().isStatic()) {
      addClassInitializerTarget(field.getHolder());
    }
  }

  private void processInitClass(DexType type) {
    DexType rewrittenType = appViewWithLiveness.graphLens().lookupType(type);
    if (rewrittenType.isIntType()) {
      // Type was unboxed; init-class instruction will be removed by enum unboxer.
      assert appViewWithLiveness.hasUnboxedEnums();
      assert appViewWithLiveness.unboxedEnums().isUnboxedEnum(type);
      return;
    }
    DexProgramClass clazz = asProgramClassOrNull(appViewWithLiveness.definitionFor(rewrittenType));
    if (clazz == null) {
      assert false;
      return;
    }
    addClassInitializerTarget(clazz);
  }

  @Override
  protected void processSingleTarget(ProgramMethod singleTarget, ProgramMethod context) {
    super.processSingleTarget(singleTarget, context);
    if (singleTarget.getAccessFlags().isStatic()) {
      addClassInitializerTarget(singleTarget.getHolder());
    }
  }

  @Override
  public void registerInitClass(DexType clazz) {
    processInitClass(clazz);
  }

  @Override
  public void registerInstanceFieldRead(DexField field) {
    processFieldRead(field);
  }

  @Override
  public void registerInstanceFieldWrite(DexField field) {
    processFieldWrite(field);
  }

  @Override
  public void registerInstanceOf(DexType type) {}

  @Override
  public void registerNewInstance(DexType type) {
    if (type.isClassType()) {
      addClassInitializerTarget(type);
    }
  }

  @Override
  public void registerStaticFieldRead(DexField field) {
    processFieldRead(field);
  }

  @Override
  public void registerStaticFieldWrite(DexField field) {
    processFieldWrite(field);
  }

  @Override
  public void registerTypeReference(DexType type) {}

  @Override
  public void registerCallSite(DexCallSite callSite) {
    registerMethodHandle(
        callSite.bootstrapMethod, MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
  }
}
