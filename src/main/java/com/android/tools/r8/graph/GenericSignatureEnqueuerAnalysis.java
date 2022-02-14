// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.analysis.EnqueuerAnalysis;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.Enqueuer.EnqueuerDefinitionSupplier;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.google.common.collect.Sets;
import java.util.Set;

public class GenericSignatureEnqueuerAnalysis extends EnqueuerAnalysis {

  private final EnqueuerDefinitionSupplier enqueuerDefinitionSupplier;
  private final Set<DexReference> processedSignatures = Sets.newIdentityHashSet();

  public GenericSignatureEnqueuerAnalysis(EnqueuerDefinitionSupplier enqueuerDefinitionSupplier) {
    this.enqueuerDefinitionSupplier = enqueuerDefinitionSupplier;
  }

  @Override
  public void processNewlyLiveClass(DexProgramClass clazz, EnqueuerWorklist worklist) {
    processSignature(clazz, clazz.getContext());
  }

  @Override
  public void notifyMarkFieldAsReachable(ProgramField field, EnqueuerWorklist worklist) {
    processSignature(field, field.getContext());
  }

  @Override
  public void processNewlyLiveField(
      ProgramField field, ProgramDefinition context, EnqueuerWorklist worklist) {
    processSignature(field, context);
  }

  @Override
  public void notifyMarkMethodAsTargeted(ProgramMethod method, EnqueuerWorklist worklist) {
    processSignature(method, method.getContext());
  }

  @Override
  public void processNewlyLiveMethod(
      ProgramMethod method,
      ProgramDefinition context,
      Enqueuer enqueuer,
      EnqueuerWorklist worklist) {
    processSignature(method, context);
  }

  private void processSignature(ProgramDefinition signatureHolder, ProgramDefinition context) {
    if (!processedSignatures.add(signatureHolder.getReference())) {
      return;
    }
    GenericSignatureTypeVisitor genericSignatureTypeVisitor =
        new GenericSignatureTypeVisitor(context, enqueuerDefinitionSupplier::definitionFor);
    if (signatureHolder.isClass()) {
      genericSignatureTypeVisitor.visitClassSignature(
          signatureHolder.asClass().getClassSignature());
    } else if (signatureHolder.isMethod()) {
      genericSignatureTypeVisitor.visitMethodSignature(
          signatureHolder.asMethod().getDefinition().getGenericSignature());
    } else {
      assert signatureHolder.isField();
      genericSignatureTypeVisitor.visitFieldTypeSignature(
          signatureHolder.asField().getDefinition().getGenericSignature());
    }
  }
}
