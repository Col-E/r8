// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.code.CfOrDexInstruction;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoEnqueuerExtension;
import com.android.tools.r8.shaking.DefaultEnqueuerUseRegistry;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerUseRegistryFactory;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.ListIterator;
import java.util.function.BiFunction;

public class ProtoEnqueuerUseRegistry extends DefaultEnqueuerUseRegistry {

  private static final EnqueuerUseRegistryFactory FACTORY = ProtoEnqueuerUseRegistry::new;

  private final ProtoReferences references;

  public ProtoEnqueuerUseRegistry(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod currentMethod,
      Enqueuer enqueuer,
      BiFunction<DexReference, AndroidApiLevel, AndroidApiLevel> apiLevelReferenceMap) {
    super(appView, currentMethod, enqueuer, apiLevelReferenceMap);
    this.references = appView.protoShrinker().references;
  }

  public static EnqueuerUseRegistryFactory getFactory() {
    return FACTORY;
  }

  /**
   * Unlike {@link DefaultEnqueuerUseRegistry#registerConstClass(DexType, ListIterator)}, this
   * method does not trace any const-class instructions in every implementation of dynamicMethod().
   *
   * <p>The const-class instructions that remain after the proto schema has been optimized will be
   * traced manually by {@link ProtoEnqueuerExtension#tracePendingInstructionsInDynamicMethods}.
   */
  @Override
  public void registerConstClass(
      DexType type, ListIterator<? extends CfOrDexInstruction> iterator) {
    if (references.isDynamicMethod(getContextMethod())) {
      enqueuer.addDeadProtoTypeCandidate(type);
      return;
    }
    super.registerConstClass(type, iterator);
  }

  /**
   * Unlike {@link DefaultEnqueuerUseRegistry#registerStaticFieldRead(DexField)}, this method does
   * not trace any static-get instructions in every implementation of dynamicMethod() that accesses
   * an 'INSTANCE' or a 'DEFAULT_INSTANCE' field.
   *
   * <p>The motivation for this is that the proto shrinker will optimize the proto schemas in each
   * dynamicMethod() after the second round of tree shaking. This is done by {@link
   * GeneratedMessageLiteShrinker#postOptimizeDynamicMethods}. If we traced all static field reads
   * as the {@link DefaultEnqueuerUseRegistry} we would end up retaining the types that are
   * references from the non-optimized proto schemas, but which do not end up being referenced from
   * the final optimized proto schemas.
   *
   * <p>The static-get instructions that remain after the proto schema has been optimized will be
   * traced manually by {@link ProtoEnqueuerExtension#tracePendingInstructionsInDynamicMethods}.
   */
  @Override
  public void registerStaticFieldRead(DexField field) {
    if (references.isDynamicMethod(getContextMethod())
        && field.getHolderType() != getContextHolder().getType()
        && isStaticFieldReadForProtoSchemaDefinition(field)) {
      enqueuer.addDeadProtoTypeCandidate(field.getHolderType());
      return;
    }
    super.registerStaticFieldRead(field);
  }

  private boolean isStaticFieldReadForProtoSchemaDefinition(DexField field) {
    if (field == references.getDefaultInstanceField(getContextHolder())) {
      return true;
    }
    DexProgramClass holder = asProgramClassOrNull(appView.definitionFor(field.getHolderType()));
    return holder != null
        && holder.getInterfaces().contains(references.enumVerifierType)
        && field == references.getEnumVerifierInstanceField(holder);
  }
}
