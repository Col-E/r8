// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoFieldTypeFactory;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.Set;

public class ProtoShrinker {

  public final RawMessageInfoDecoder decoder;
  public final ProtoFieldTypeFactory factory;
  public final GeneratedExtensionRegistryShrinker generatedExtensionRegistryShrinker;
  public final GeneratedMessageLiteShrinker generatedMessageLiteShrinker;
  public final GeneratedMessageLiteBuilderShrinker generatedMessageLiteBuilderShrinker;
  public final EnumLiteProtoShrinker enumProtoShrinker;
  public final ProtoReferences references;

  private Set<DexType> deadProtoTypes = Sets.newIdentityHashSet();

  public ProtoShrinker(AppView<AppInfoWithLiveness> appView) {
    ProtoFieldTypeFactory factory = new ProtoFieldTypeFactory();
    ProtoReferences references = new ProtoReferences(appView.dexItemFactory());
    this.decoder = new RawMessageInfoDecoder(factory, references);
    this.factory = factory;
    this.generatedExtensionRegistryShrinker =
        appView.options().protoShrinking().enableGeneratedExtensionRegistryShrinking
            ? new GeneratedExtensionRegistryShrinker(appView, references)
            : null;
    this.generatedMessageLiteShrinker =
        appView.options().protoShrinking().enableGeneratedMessageLiteShrinking
            ? new GeneratedMessageLiteShrinker(appView, decoder, references)
            : null;
    this.generatedMessageLiteBuilderShrinker =
        appView.options().protoShrinking().enableGeneratedMessageLiteBuilderShrinking
            ? new GeneratedMessageLiteBuilderShrinker(appView, references)
            : null;
    this.enumProtoShrinker =
        appView.options().protoShrinking().isProtoEnumShrinkingEnabled()
            ? new EnumLiteProtoShrinker(appView, references)
            : null;
    this.references = references;
  }

  public Set<DexType> getDeadProtoTypes() {
    return deadProtoTypes;
  }

  public void setDeadProtoTypes(Set<DexType> deadProtoTypes) {
    // We should only need to keep track of the dead proto types for assertion purposes.
    InternalOptions.checkAssertionsEnabled();
    this.deadProtoTypes = deadProtoTypes;
  }

  public boolean verifyDeadProtoTypesNotReferenced(VirtualFile virtualFile) {
    for (DexType deadProtoType : deadProtoTypes) {
      assert !virtualFile.containsString(deadProtoType.descriptor);
      assert !virtualFile.containsType(deadProtoType);
    }
    return true;
  }
}
