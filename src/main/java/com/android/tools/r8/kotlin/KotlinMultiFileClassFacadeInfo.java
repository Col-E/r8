// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import kotlinx.metadata.jvm.KotlinClassMetadata.MultiFileClassFacade;

// Holds information about Metadata.MultiFileClassFace
public class KotlinMultiFileClassFacadeInfo implements KotlinClassLevelInfo {

  private final List<DexType> partClassNames;

  private KotlinMultiFileClassFacadeInfo(List<DexType> partClassNames) {
    this.partClassNames = partClassNames;
  }

  static KotlinMultiFileClassFacadeInfo create(
      MultiFileClassFacade kmMultiFileClassFacade, DexDefinitionSupplier appView) {
    ImmutableList.Builder<DexType> builder = ImmutableList.builder();
    for (String partClassName : kmMultiFileClassFacade.getPartClassNames()) {
      String descriptor = DescriptorUtils.getDescriptorFromClassBinaryName(partClassName);
      DexType type = appView.dexItemFactory().createType(descriptor);
      builder.add(type);
    }
    return new KotlinMultiFileClassFacadeInfo(builder.build());
  }

  @Override
  public boolean isMultiFileFacade() {
    return true;
  }

  @Override
  public KotlinMultiFileClassFacadeInfo asMultiFileFacade() {
    return this;
  }

  @Override
  public KotlinClassHeader rewrite(
      DexClass clazz, AppView<AppInfoWithLiveness> appView, NamingLens namingLens) {
    KotlinClassMetadata.MultiFileClassFacade.Writer writer =
        new KotlinClassMetadata.MultiFileClassFacade.Writer();
    List<String> partClassNameStrings = new ArrayList<>(partClassNames.size());
    for (DexType partClassName : partClassNames) {
      if (appView.appInfo().isNonProgramTypeOrLiveProgramType(partClassName)) {
        DexString descriptor = namingLens.lookupDescriptor(partClassName);
        String classifier = DescriptorUtils.descriptorToKotlinClassifier(descriptor.toString());
        partClassNameStrings.add(classifier);
      }
    }
    return writer.write(partClassNameStrings).getHeader();
  }
}
