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
    // For a program class, make sure it is live.
    if (!appView.appInfo().isNonProgramTypeOrLiveProgramType(type)) {
      return null;
    }
    DexType renamedType = lens.lookupType(type, appView.dexItemFactory());
    // For library or classpath class, we should not have renamed it.
    assert clazz.isProgramClass() || renamedType == type
        : type.toSourceString() + " -> " + renamedType.toSourceString();
    // TODO(b/70169921): Consult kotlinx.metadata.Flag for kotlin-specific flags (e.g., sealed).
    KmType kmType = new KmType(clazz.accessFlags.getAsCfAccessFlags());
    kmType.visitClass(DescriptorUtils.descriptorToInternalName(renamedType.toDescriptorString()));
    return kmType;
  }
}
