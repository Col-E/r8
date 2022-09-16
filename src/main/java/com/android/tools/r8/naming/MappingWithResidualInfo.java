// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.naming.MemberNaming.Signature;
import java.util.function.Function;

public interface MappingWithResidualInfo {

  String getRenamedName();

  Signature getOriginalSignature();

  boolean hasResidualSignature();

  Signature getResidualSignatureInternal();

  void setResidualSignatureInternal(Signature signature);

  default Signature computeResidualSignature(Function<String, String> typeNameMapper) {
    if (hasResidualSignature()) {
      return getResidualSignatureInternal();
    }
    Signature residualSignature =
        getOriginalSignature().computeResidualSignature(getRenamedName(), typeNameMapper);
    setResidualSignatureInternal(residualSignature);
    return residualSignature;
  }
}
