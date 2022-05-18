// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Box;
import java.util.function.Consumer;
import kotlinx.metadata.jvm.JvmFieldSignature;

/**
 * The JvmSignature for a method or property does not always correspond to the actual signature, see
 * b/154201250. We therefore need to model the signature as well.
 */
public class KotlinJvmFieldSignatureInfo implements EnqueuerMetadataTraceable {

  private final KotlinTypeReference type;
  private final String name;

  private KotlinJvmFieldSignatureInfo(String name, KotlinTypeReference type) {
    this.name = name;
    this.type = type;
  }

  public static KotlinJvmFieldSignatureInfo create(
      JvmFieldSignature fieldSignature, DexItemFactory factory) {
    if (fieldSignature == null) {
      return null;
    }
    return new KotlinJvmFieldSignatureInfo(
        fieldSignature.getName(),
        KotlinTypeReference.fromDescriptor(fieldSignature.getDesc(), factory));
  }

  boolean rewrite(Consumer<JvmFieldSignature> consumer, DexEncodedField field, AppView<?> appView) {
    boolean rewritten = false;
    String finalName = name;
    if (field != null) {
      String fieldName = field.getReference().name.toString();
      String rewrittenName = appView.getNamingLens().lookupName(field.getReference()).toString();
      if (!fieldName.equals(rewrittenName)) {
        rewritten = true;
        finalName = rewrittenName;
      }
    }
    String defValue = appView.dexItemFactory().objectType.toDescriptorString();
    Box<String> renamedDescriptor = new Box<>();
    rewritten |= type.toRenamedDescriptorOrDefault(renamedDescriptor::set, appView, defValue);
    consumer.accept(new JvmFieldSignature(finalName, renamedDescriptor.get()));
    return rewritten;
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    type.trace(definitionSupplier);
  }
}
