// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromClassBinaryName;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.origin.GlobalSyntheticOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.MainDexInfo;
import java.util.Comparator;

/**
 * A synthesizing context is a description of the context that gives rise to a synthetic item.
 *
 * <p>Note that a context can only itself be a synthetic item if it was provided as an input that
 * was marked as synthetic already, in which case the context consists of the synthetic input type
 * as well as the original synthesizing context type specified in it synthesis annotation.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SynthesizingContext implements Comparable<SynthesizingContext> {

  // The synthesizing context is the type used for ensuring a hygienic placement of a synthetic.
  // Thus this type will potentially be used as the prefix of a synthetic class.
  private final DexType synthesizingContextType;

  // The input context is the program input type that is the actual context of a synthetic.
  // In particular, if the synthetic type is itself a program input, then it will be its own
  // input context but it will have a distinct synthesizing context (encoded in its annotation).
  private final DexType inputContextType;
  private final Origin inputContextOrigin;

  private final FeatureSplit featureSplit;

  static SynthesizingContext fromNonSyntheticInputContext(ClasspathOrLibraryClass context) {
    // A context that is itself non-synthetic is the single context, thus both the input context
    // and synthesizing context coincide.
    return new SynthesizingContext(
        context.getContextType(),
        context.getContextType(),
        context.getOrigin(),
        // Synthesizing from a non-program context is just considered to be "base".
        FeatureSplit.BASE);
  }

  static SynthesizingContext fromType(DexType type) {
    // This method should only be used for synthesizing from a non-program context!
    // Thus we have no origin info and place the context in the "base" feature.
    return new SynthesizingContext(type, type, GlobalSyntheticOrigin.instance(), FeatureSplit.BASE);
  }

  static SynthesizingContext fromNonSyntheticInputContext(
      ProgramDefinition context, FeatureSplit featureSplit) {
    // A context that is itself non-synthetic is the single context, thus both the input context
    // and synthesizing context coincide.
    return new SynthesizingContext(
        context.getContextType(), context.getContextType(), context.getOrigin(), featureSplit);
  }

  static SynthesizingContext fromSyntheticInputClass(
      DexProgramClass clazz, DexType synthesizingContextType, AppView<?> appView) {
    // A context that is itself synthetic must denote a synthesizing context from which to ensure
    // hygiene. This synthesizing context type is encoded on the synthetic for intermediate builds.
    FeatureSplit featureSplit;
    if (appView.hasClassHierarchy()) {
      AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy =
          appView.withClassHierarchy();
      featureSplit =
          appViewWithClassHierarchy
              .appInfo()
              .getClassToFeatureSplitMap()
              .getFeatureSplit(clazz, appViewWithClassHierarchy);
    } else {
      featureSplit = FeatureSplit.BASE;
    }
    return new SynthesizingContext(synthesizingContextType, clazz.type, clazz.origin, featureSplit);
  }

  private SynthesizingContext(
      DexType synthesizingContextType,
      DexType inputContextType,
      Origin inputContextOrigin,
      FeatureSplit featureSplit) {
    this.synthesizingContextType = synthesizingContextType;
    this.inputContextType = inputContextType;
    this.inputContextOrigin = inputContextOrigin;
    this.featureSplit = featureSplit;
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isSyntheticInputClass() {
    return synthesizingContextType != inputContextType;
  }

  @Override
  public int compareTo(SynthesizingContext other) {
    return Comparator
        // The first item to compare is the synthesizing context type. This is the type used to
        // choose the context prefix for items.
        .comparing(SynthesizingContext::getSynthesizingContextType)
        // To ensure that equals coincides with compareTo == 0, we then compare 'type'.
        // Also, the input type context is used as the hygienic prefix in intermediate modes.
        .thenComparing(c -> c.inputContextType)
        .compare(this, other);
  }

  DexType getSynthesizingContextType() {
    return synthesizingContextType;
  }

  DexType getSynthesizingInputContext(boolean intermediate) {
    return intermediate ? inputContextType : getSynthesizingContextType();
  }

  Origin getInputContextOrigin() {
    return inputContextOrigin;
  }

  FeatureSplit getFeatureSplit() {
    return featureSplit;
  }

  @SuppressWarnings("ReferenceEquality")
  SynthesizingContext rewrite(NonIdentityGraphLens lens) {
    DexType rewrittenInputContextType = lens.lookupType(inputContextType);
    DexType rewrittenSynthesizingContextType = lens.lookupType(synthesizingContextType);
    return rewrittenInputContextType == inputContextType
            && rewrittenSynthesizingContextType == synthesizingContextType
        ? this
        : new SynthesizingContext(
            rewrittenSynthesizingContextType,
            rewrittenInputContextType,
            inputContextOrigin,
            featureSplit);
  }

  void registerPrefixRewriting(DexType hygienicType, AppView<?> appView) {
    if (!appView.options().isDesugaredLibraryCompilation()) {
      return;
    }
    assert hygienicType.toSourceString().startsWith(synthesizingContextType.toSourceString());
    DexType rewrittenContext = appView.typeRewriter.rewrittenContextType(synthesizingContextType);
    if (rewrittenContext == null) {
      return;
    }
    String contextPrefix =
        getBinaryNameFromDescriptor(synthesizingContextType.toDescriptorString());
    String rewrittenPrefix = getBinaryNameFromDescriptor(rewrittenContext.toDescriptorString());
    String suffix =
        getBinaryNameFromDescriptor(hygienicType.toDescriptorString())
            .substring(contextPrefix.length());
    DexType rewrittenType =
        appView
            .dexItemFactory()
            .createType(getDescriptorFromClassBinaryName(rewrittenPrefix + suffix));
    appView.typeRewriter.rewriteType(hygienicType, rewrittenType);
  }

  @Override
  public String toString() {
    return "SynthesizingContext{"
        + getSynthesizingContextType()
        + (!featureSplit.isBase() ? ", feature:" + featureSplit : "")
        + "}";
  }

  // TODO(b/181858113): Remove once deprecated main-dex-list is removed.
  boolean isDerivedFromMainDexList(MainDexInfo mainDexInfo) {
    return mainDexInfo.isSyntheticContextOnMainDexList(inputContextType);
  }
}
