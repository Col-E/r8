// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import java.util.List;
import java.util.stream.Collectors;
import kotlinx.metadata.KmTypeParameter;

public interface KmTypeParameterSubjectMixin {

  List<KmTypeParameter> getKmTypeParameters();

  CodeInspector getCodeInspector();

  default List<KmTypeParameterSubject> typeParameters() {
    CodeInspector codeInspector = getCodeInspector();
    return getKmTypeParameters().stream()
        .map(kmTypeParam -> new FoundKmTypeParameterSubject(codeInspector, kmTypeParam))
        .collect(Collectors.toList());
  }

  default KmTypeParameterSubject kmTypeParameterWithUniqueName(String name) {
    FoundKmTypeParameterSubject typeSubject = null;
    for (KmTypeParameter kmTypeParameter : getKmTypeParameters()) {
      if (kmTypeParameter.getName().equals(name)) {
        assert typeSubject == null;
        typeSubject = new FoundKmTypeParameterSubject(getCodeInspector(), kmTypeParameter);
      }
    }
    return typeSubject != null ? typeSubject : new AbsentKmTypeParameterSubject();
  }
}
