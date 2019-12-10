// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import kotlinx.metadata.KmType;

class KotlinMetadataSynthesizer {
  static KmType toRenamedKmType(
      DexType type, AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null) {
      return null;
    }
    // For library or classpath class, synthesize @Metadata always.
    if (clazz.isNotProgramClass()) {
      KmType kmType = new KmType(clazz.accessFlags.getAsCfAccessFlags());
      assert type == lens.lookupType(type, appView.dexItemFactory());
      kmType.visitClass(DescriptorUtils.descriptorToInternalName(type.toDescriptorString()));
      return kmType;
    }
    // From now on, it is a program class. First, make sure it is live.
    if (!appView.appInfo().isLiveProgramType(type)) {
      return null;
    }
    KmType kmType = new KmType(clazz.accessFlags.getAsCfAccessFlags());
    DexType renamedType = lens.lookupType(type, appView.dexItemFactory());
    kmType.visitClass(DescriptorUtils.descriptorToInternalName(renamedType.toDescriptorString()));
    return kmType;
  }
}
