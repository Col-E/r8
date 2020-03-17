// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.toRenamedBinaryName;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public final class KotlinClassFacade extends KotlinInfo<KotlinClassMetadata.MultiFileClassFacade> {

  // TODO(b/151194869): is it better to maintain List<DexType>?
  List<String> partClassNames;

  static KotlinClassFacade fromKotlinClassMetadata(
      KotlinClassMetadata kotlinClassMetadata, DexClass clazz) {
    assert kotlinClassMetadata instanceof KotlinClassMetadata.MultiFileClassFacade;
    KotlinClassMetadata.MultiFileClassFacade multiFileClassFacade =
        (KotlinClassMetadata.MultiFileClassFacade) kotlinClassMetadata;
    return new KotlinClassFacade(multiFileClassFacade, clazz);
  }

  private KotlinClassFacade(KotlinClassMetadata.MultiFileClassFacade metadata, DexClass clazz) {
    super(metadata, clazz);
  }

  @Override
  void processMetadata(KotlinClassMetadata.MultiFileClassFacade metadata) {
    // Part Class names are stored in `d1`, which is immutable. Make a copy instead.
    partClassNames = new ArrayList<>(metadata.getPartClassNames());
    // No API to explore metadata details, hence nothing further to do.
  }

  @Override
  void rewrite(AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    ListIterator<String> partClassIterator = partClassNames.listIterator();
    while (partClassIterator.hasNext()) {
      String partClassName = partClassIterator.next();
      partClassIterator.remove();
      DexType partClassType = appView.dexItemFactory().createType(
          DescriptorUtils.getDescriptorFromClassBinaryName(partClassName));
      String renamedPartClassName = toRenamedBinaryName(partClassType, appView, lens);
      if (renamedPartClassName != null) {
        partClassIterator.add(renamedPartClassName);
      }
    }
  }

  @Override
  KotlinClassHeader createHeader() {
    KotlinClassMetadata.MultiFileClassFacade.Writer writer =
        new KotlinClassMetadata.MultiFileClassFacade.Writer();
    return writer.write(partClassNames).getHeader();
  }

  @Override
  public Kind getKind() {
    return Kind.Facade;
  }

  @Override
  public boolean isClassFacade() {
    return true;
  }

  @Override
  public KotlinClassFacade asClassFacade() {
    return this;
  }

  @Override
  public String toString(String indent) {
    return indent + "MultiFileClassFacade(" + StringUtils.join(partClassNames, ", ") + ")";
  }
}
