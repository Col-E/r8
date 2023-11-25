// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.naming.NamingLens.NonIdentityNamingLens;
import com.android.tools.r8.utils.InternalOptions;
import java.util.IdentityHashMap;
import java.util.Map;

// Naming lens for VarHandle desugaring rewriting. Rewriting java.lang.invoke.MethodHandles$Lookup
// to com.android.tools.r8.DesugarMethodHandlesLookup.
public class VarHandleDesugaringRewritingNamingLens extends NonIdentityNamingLens {

  private final DexItemFactory factory;
  private final NamingLens namingLens;
  private final Map<DexType, DexString> mapping;

  @SuppressWarnings("ReferenceEquality")
  public static NamingLens createVarHandleDesugaringRewritingNamingLens(AppView<?> appView) {
    DexItemFactory factory = appView.dexItemFactory();
    if (appView.options().shouldDesugarVarHandle()
        && (appView.appInfo().definitionForWithoutExistenceAssert(factory.lookupType) != null
            || appView.appInfo().definitionForWithoutExistenceAssert(factory.varHandleType)
                != null)) {

      // Prune all inner classes attributes referring to MethodHandles$Lookup, as that is rewritten
      // to the toplevel class DesugarMethodHandlesLookup.
      appView
          .appInfo()
          .classes()
          .forEach(
              clazz -> {
                clazz.removeInnerClasses(
                    innerClassAttribute -> innerClassAttribute.getInner() == factory.lookupType);
              });

      // Function to prefix type namespace, e.g. rename L... to Lj$/...
      Map<DexType, DexString> mapping = new IdentityHashMap<>();
      addRewritingForGlobalSynthetic(
          appView, factory.lookupType, factory.desugarMethodHandlesLookupType, mapping);
      addRewritingForGlobalSynthetic(
          appView, factory.varHandleType, factory.desugarVarHandleType, mapping);
      return new VarHandleDesugaringRewritingNamingLens(appView, mapping);
    }
    return appView.getNamingLens();
  }

  private static void addRewritingForGlobalSynthetic(
      AppView<?> appView,
      DexType globalSynthetic,
      DexType desugaredGlobalSynthetic,
      Map<DexType, DexString> mapping) {
    DexItemFactory factory = appView.dexItemFactory();
    // The VarHandle global synthetics and synthetics derived from them are rewritten to use the
    // desugared name.
    assert appView.appInfo().getSyntheticItems().isFinalized();
    String globalSyntheticString = globalSynthetic.descriptor.toString();
    DexString currentPrefix =
        factory.createString(
            globalSyntheticString.substring(0, globalSyntheticString.length() - 1));
    String desugaredGlobalSyntheticString = desugaredGlobalSynthetic.descriptor.toString();
    DexString newPrefix =
        appView.options().synthesizedClassPrefix.isEmpty()
            ? factory.createString(
                "L"
                    + desugaredGlobalSyntheticString.substring(
                        1, desugaredGlobalSyntheticString.length() - 1))
            : factory.createString(
                "L"
                    + appView.options().synthesizedClassPrefix
                    + desugaredGlobalSyntheticString.substring(
                        1, desugaredGlobalSyntheticString.length() - 1));
    // Rewrite the global synthetic in question and all the synthetics derived from it.
    appView
        .appInfo()
        .getSyntheticItems()
        .collectSyntheticsFromContext(globalSynthetic)
        .forEach(
            synthetic ->
                mapping.put(
                    synthetic,
                    synthetic.descriptor.withNewPrefix(currentPrefix, newPrefix, factory)));
  }

  private VarHandleDesugaringRewritingNamingLens(
      AppView<?> appView, Map<DexType, DexString> mapping) {
    super(appView.dexItemFactory());
    this.factory = appView.dexItemFactory();
    this.namingLens = appView.getNamingLens();
    this.mapping = mapping;
  }

  private boolean isRenamed(DexType type) {
    return getRenaming(type) != null;
  }

  @SuppressWarnings("ReferenceEquality")
  private DexString getRenaming(DexType type) {
    assert type != factory.desugarMethodHandlesLookupType;
    assert type != factory.desugarVarHandleType;
    return mapping.get(type);
  }

  @Override
  protected DexString internalLookupClassDescriptor(DexType type) {
    DexString renaming = getRenaming(type);
    return renaming != null ? renaming : namingLens.lookupDescriptor(type);
  }

  @Override
  public DexString lookupInnerName(InnerClassAttribute attribute, InternalOptions options) {
    assert !isRenamed(attribute.getInner());
    return namingLens.lookupInnerName(attribute, options);
  }

  @Override
  public DexString lookupName(DexMethod method) {
    // VarHandle desugaring rewriting does not influence method name.
    return namingLens.lookupName(method);
  }

  @Override
  public DexString lookupName(DexField field) {
    // VarHandle desugaring rewriting does not influence method name.
    return namingLens.lookupName(field);
  }

  @Override
  public boolean hasPrefixRewritingLogic() {
    return namingLens.hasPrefixRewritingLogic();
  }

  @Override
  public DexString prefixRewrittenType(DexType type) {
    return namingLens.prefixRewrittenType(type);
  }

  @Override
  public String lookupPackageName(String packageName) {
    return namingLens.lookupPackageName(packageName);
  }

  @Override
  public boolean verifyRenamingConsistentWithResolution(DexMethod item) {
    return namingLens.verifyRenamingConsistentWithResolution(item);
  }
}
