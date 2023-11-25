// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
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

// Naming lens for rewriting java.lang.Record to the internal RecordTag type.
public class RecordRewritingNamingLens extends NonIdentityNamingLens {

  private final DexItemFactory factory;
  private final NamingLens namingLens;

  public static NamingLens createRecordRewritingNamingLens(AppView<?> appView) {
    if (appView.options().shouldDesugarRecords()
        && appView
                .appInfo()
                .definitionForWithoutExistenceAssert(appView.dexItemFactory().recordType)
            != null) {
      return new RecordRewritingNamingLens(appView);
    }
    return appView.getNamingLens();
  }

  public RecordRewritingNamingLens(AppView<?> appView) {
    super(appView.dexItemFactory());
    this.factory = appView.dexItemFactory();
    this.namingLens = appView.getNamingLens();
  }

  private boolean isRenamed(DexType type) {
    return getRenaming(type) != null;
  }

  @SuppressWarnings("ReferenceEquality")
  private DexString getRenaming(DexType type) {
    if (type == factory.recordType) {
      return factory.recordTagType.descriptor;
    }
    return null;
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
    // Record rewriting does not influence method name.
    return namingLens.lookupName(method);
  }

  @Override
  public DexString lookupName(DexField field) {
    // Record rewriting does not influence field name.
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
