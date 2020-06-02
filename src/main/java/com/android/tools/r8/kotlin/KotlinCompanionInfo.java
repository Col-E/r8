// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.naming.NamingLens;
import kotlinx.metadata.KmClassVisitor;

// Structure around a kotlin companion object that can be assigned to a field.
public class KotlinCompanionInfo implements KotlinFieldLevelInfo {

  @Override
  public boolean isCompanion() {
    return true;
  }

  @Override
  public KotlinCompanionInfo asCompanion() {
    return this;
  }

  public void rewrite(KmClassVisitor visitor, DexField field, NamingLens lens) {
    DexString dexString = lens.lookupName(field);
    String finalName = dexString.toString();
    visitor.visitCompanionObject(finalName);
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    // Do nothing.
  }
}
