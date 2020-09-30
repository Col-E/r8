// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.naming.NamingLens.NonIdentityNamingLens;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Naming lens for rewriting type prefixes.
public class PrefixRewritingNamingLens extends NonIdentityNamingLens {

  final NamingLens namingLens;
  final InternalOptions options;
  final AppView<?> appView;

  public static NamingLens createPrefixRewritingNamingLens(AppView<?> appView) {
    return createPrefixRewritingNamingLens(appView, NamingLens.getIdentityLens());
  }

  public static NamingLens createPrefixRewritingNamingLens(
      AppView<?> appView, NamingLens namingLens) {
    if (!appView.rewritePrefix.isRewriting()) {
      return namingLens;
    }
    return new PrefixRewritingNamingLens(namingLens, appView);
  }

  public PrefixRewritingNamingLens(NamingLens namingLens, AppView<?> appView) {
    super(appView.dexItemFactory());
    this.appView = appView;
    this.namingLens = namingLens;
    this.options = appView.options();
  }

  private boolean isRenamed(DexType type) {
    return getRenaming(type) != null;
  }

  private DexString getRenaming(DexType type) {
    DexString descriptor = null;
    if (appView.rewritePrefix.hasRewrittenType(type, appView)) {
      descriptor = appView.rewritePrefix.rewrittenType(type, appView).descriptor;
    }
    return descriptor;
  }

  @Override
  public boolean hasPrefixRewritingLogic() {
    return true;
  }

  @Override
  public DexString prefixRewrittenType(DexType type) {
    return getRenaming(type);
  }

  @Override
  protected DexString internalLookupClassDescriptor(DexType type) {
    DexString renaming = getRenaming(type);
    return renaming != null ? renaming : namingLens.lookupDescriptor(type);
  }

  @Override
  public DexString lookupInnerName(InnerClassAttribute attribute, InternalOptions options) {
    if (isRenamed(attribute.getInner())) {
      // Prefix rewriting does not influence the inner name.
      return attribute.getInnerName();
    }
    return namingLens.lookupInnerName(attribute, options);
  }

  @Override
  public DexString lookupName(DexMethod method) {
    if (isRenamed(method.holder)) {
      // Prefix rewriting does not influence the method name.
      return method.name;
    }
    return namingLens.lookupName(method);
  }

  @Override
  public DexString lookupName(DexField field) {
    if (isRenamed(field.holder)) {
      // Prefix rewriting does not influence the field name.
      return field.name;
    }
    return namingLens.lookupName(field);
  }

  @Override
  public String lookupPackageName(String packageName) {
    // Used for resource shrinking.
    // Desugared libraries do not have resources.
    // Hence this call is necessarily for the minifyingLens.
    // TODO(b/134732760): This assertion does not hold with ressources with renamed prefixes.
    // Write a test where the assertion does not hold and fix it.
    assert verifyNotPrefixRewrittenPackage(packageName);
    return namingLens.lookupPackageName(packageName);
  }

  private boolean verifyNotPrefixRewrittenPackage(String packageName) {
    appView.rewritePrefix.forAllRewrittenTypes(
        dexType -> {
          assert !dexType.getPackageDescriptor().equals(packageName);
        });
    return true;
  }

  @Override
  public <T extends DexItem> Map<String, T> getRenamedItems(
      Class<T> clazz, Predicate<T> predicate, Function<T, String> namer) {
    Map<String, T> renamedItemsPrefixRewritting;
    if (clazz == DexType.class) {
      renamedItemsPrefixRewritting = new HashMap<>();
      appView.rewritePrefix.forAllRewrittenTypes(
          item -> {
            T cast = clazz.cast(item);
            if (predicate.test(cast)) {
              renamedItemsPrefixRewritting.put(namer.apply(cast), cast);
            }
          });
    } else {
      renamedItemsPrefixRewritting = Collections.emptyMap();
    }
    Map<String, T> renamedItemsMinifier = namingLens.getRenamedItems(clazz, predicate, namer);
    // The Collector throws an exception for duplicated keys.
    return Stream.concat(
            renamedItemsPrefixRewritting.entrySet().stream(),
            renamedItemsMinifier.entrySet().stream())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public boolean verifyRenamingConsistentWithResolution(DexMethod item) {
    return namingLens.verifyRenamingConsistentWithResolution(item);
  }
}
