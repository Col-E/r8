// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.naming.NamingLens.NonIdentityNamingLens;
import com.android.tools.r8.utils.InternalOptions;

// Naming lens for rewriting type prefixes.
public class PrefixRewritingNamingLens extends NonIdentityNamingLens {

  private final AppView<?> appView;
  private final NamingLens namingLens;

  public static NamingLens createPrefixRewritingNamingLens(AppView<?> appView) {
    if (!appView.typeRewriter.isRewriting()) {
      return appView.getNamingLens();
    }
    return new PrefixRewritingNamingLens(appView);
  }

  public PrefixRewritingNamingLens(AppView<?> appView) {
    super(appView.dexItemFactory());
    this.appView = appView;
    this.namingLens = appView.getNamingLens();
  }

  private boolean isRenamed(DexType type) {
    return getRenaming(type) != null;
  }

  private DexString getRenaming(DexType type) {
    DexString descriptor = null;
    if (appView.typeRewriter.hasRewrittenType(type, appView)) {
      descriptor = appView.typeRewriter.rewrittenType(type, appView).descriptor;
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
    appView.typeRewriter.forAllRewrittenTypes(
        dexType -> {
          assert !dexType.getPackageDescriptor().equals(packageName);
        });
    return true;
  }

  @Override
  public boolean verifyRenamingConsistentWithResolution(DexMethod item) {
    return namingLens.verifyRenamingConsistentWithResolution(item);
  }
}
