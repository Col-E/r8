// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.analysis.EnqueuerAnalysis;
import com.android.tools.r8.shaking.Enqueuer.EnqueuerDefinitionSupplier;
import com.android.tools.r8.shaking.EnqueuerWorklist;

public class GenericSignatureEnqueuerAnalysis extends EnqueuerAnalysis {

  private final EnqueuerDefinitionSupplier enqueuerDefinitionSupplier;

  public GenericSignatureEnqueuerAnalysis(EnqueuerDefinitionSupplier enqueuerDefinitionSupplier) {
    this.enqueuerDefinitionSupplier = enqueuerDefinitionSupplier;
  }

  @Override
  public void processNewlyLiveClass(DexProgramClass clazz, EnqueuerWorklist worklist) {
    new GenericSignatureTypeVisitor(clazz, enqueuerDefinitionSupplier::definitionFor)
        .visitClassSignature(clazz.getClassSignature());
  }

  @Override
  public void processNewlyLiveField(ProgramField field, ProgramDefinition context) {
    new GenericSignatureTypeVisitor(context, enqueuerDefinitionSupplier::definitionFor)
        .visitFieldTypeSignature(field.getDefinition().getGenericSignature());
  }

  @Override
  public void processNewlyLiveMethod(ProgramMethod method, ProgramDefinition context) {
    new GenericSignatureTypeVisitor(context, enqueuerDefinitionSupplier::definitionFor)
        .visitMethodSignature(method.getDefinition().getGenericSignature());
  }

}
