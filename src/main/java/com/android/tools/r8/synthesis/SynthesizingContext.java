// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromClassBinaryName;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.MainDexClasses;
import java.util.Comparator;
import java.util.Set;

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

  static SynthesizingContext fromNonSyntheticInputContext(ProgramDefinition context) {
    // A context that is itself non-synthetic is the single context, thus both the input context
    // and synthesizing context coincide.
    return new SynthesizingContext(
        context.getContextType(), context.getContextType(), context.getOrigin());
  }

  static SynthesizingContext fromSyntheticInputClass(
      DexProgramClass clazz, DexType synthesizingContextType) {
    assert synthesizingContextType != null;
    // A context that is itself synthetic must denote a synthesizing context from which to ensure
    // hygiene. This synthesizing context type is encoded on the synthetic for intermediate builds.
    return new SynthesizingContext(synthesizingContextType, clazz.type, clazz.origin);
  }

  private SynthesizingContext(
      DexType synthesizingContextType, DexType inputContextType, Origin inputContextOrigin) {
    this.synthesizingContextType = synthesizingContextType;
    this.inputContextType = inputContextType;
    this.inputContextOrigin = inputContextOrigin;
  }

  @Override
  public int compareTo(SynthesizingContext other) {
    return Comparator
        // The first item to compare is the synthesizing context type. This is the type used to
        // choose the context prefix for items.
        .comparing(SynthesizingContext::getSynthesizingContextType, DexType::slowCompareTo)
        // To ensure that equals coincides with compareTo == 0, we then compare 'type'.
        .thenComparing(c -> c.inputContextType, DexType::slowCompareTo)
        .compare(this, other);
  }

  Origin getInputContextOrigin() {
    return inputContextOrigin;
  }

  DexType createHygienicType(String syntheticId, DexItemFactory factory) {
    // If the context is a synthetic input, then use its annotated context as the hygienic context.
    String contextDesc = synthesizingContextType.toDescriptorString();
    String prefix = contextDesc.substring(0, contextDesc.length() - 1);
    String suffix = SyntheticItems.INTERNAL_SYNTHETIC_CLASS_SEPARATOR + syntheticId + ";";
    return factory.createType(prefix + suffix);
  }

  SynthesizingContext rewrite(NonIdentityGraphLens lens) {
    DexType rewrittenInputeContextType = lens.lookupType(inputContextType);
    DexType rewrittenSynthesizingContextType = lens.lookupType(synthesizingContextType);
    return rewrittenInputeContextType == inputContextType
            && rewrittenSynthesizingContextType == synthesizingContextType
        ? this
        : new SynthesizingContext(
            rewrittenSynthesizingContextType, rewrittenInputeContextType, inputContextOrigin);
  }

  DexType getSynthesizingContextType() {
    return synthesizingContextType;
  }

  void registerPrefixRewriting(DexType hygienicType, AppView<?> appView) {
    assert hygienicType.toSourceString().startsWith(synthesizingContextType.toSourceString());
    if (!appView.options().isDesugaredLibraryCompilation()) {
      return;
    }
    DexType rewrittenContext =
        appView
            .options()
            .desugaredLibraryConfiguration
            .getEmulateLibraryInterface()
            .get(synthesizingContextType);
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
    appView.rewritePrefix.rewriteType(hygienicType, rewrittenType);
  }

  void addIfDerivedFromMainDexClass(
      DexProgramClass externalSyntheticClass,
      MainDexClasses mainDexClasses,
      Set<DexType> allMainDexTypes,
      Set<DexType> derivedMainDexTypesToIgnore) {
    // The input context type (not the annotated context) determines if the derived class is to be
    // in main dex.
    // TODO(b/168584485): Once resolved allMainDexTypes == mainDexClasses.
    if (allMainDexTypes.contains(inputContextType)) {
      mainDexClasses.add(externalSyntheticClass);
      // Mark the type as to be ignored when computing main-dex placement for legacy types.
      derivedMainDexTypesToIgnore.add(inputContextType);
    }
  }
}
