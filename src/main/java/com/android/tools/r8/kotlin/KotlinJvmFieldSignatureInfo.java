// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toRenamedDescriptorOrDefault;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import kotlinx.metadata.jvm.JvmFieldSignature;

/**
 * The JvmSignature for a method or property does not always correspond to the actual signature, see
 * b/154201250. We therefore need to model the signature as well.
 */
public class KotlinJvmFieldSignatureInfo {

  private final DexType type;
  private final String name;

  private KotlinJvmFieldSignatureInfo(String name, DexType type) {
    this.name = name;
    this.type = type;
  }

  public static KotlinJvmFieldSignatureInfo create(
      JvmFieldSignature fieldSignature, AppView<?> appView) {
    if (fieldSignature == null) {
      return null;
    }
    return new KotlinJvmFieldSignatureInfo(
        fieldSignature.getName(), appView.dexItemFactory().createType(fieldSignature.getDesc()));
  }

  public JvmFieldSignature rewrite(
      DexEncodedField field, AppView<AppInfoWithLiveness> appView, NamingLens namingLens) {
    String finalName = name;
    if (field != null) {
      String fieldName = field.field.name.toString();
      String rewrittenName = namingLens.lookupName(field.field).toString();
      if (!fieldName.equals(rewrittenName)) {
        finalName = rewrittenName;
      }
    }
    String defValue = appView.dexItemFactory().objectType.toDescriptorString();
    return new JvmFieldSignature(
        finalName, toRenamedDescriptorOrDefault(type, appView, namingLens, defValue));
  }
}
