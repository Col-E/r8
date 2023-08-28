// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.errors;

import com.android.tools.r8.Keep;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.google.common.collect.ImmutableList;
import java.util.List;

@Keep
public class DuplicateTypeInProgramAndLibraryDiagnostic extends DuplicateTypesDiagnostic {

  public DuplicateTypeInProgramAndLibraryDiagnostic(
      ClassReference type, Origin programOrigin, Origin libraryOrigin) {
    super(type, ImmutableList.of(programOrigin, libraryOrigin));
  }

  /** Get the origin of the program definition for the duplicated type. */
  public Origin getProgramOrigin() {
    return ((List<Origin>) getOrigins()).get(0);
  }

  /** Get the origin of the library definition for the duplicated type. */
  public Origin getLibraryOrigin() {
    return ((List<Origin>) getOrigins()).get(1);
  }

  @Override
  public String getDiagnosticMessage() {
    String typeName = getType().getTypeName();
    return "Type "
        + typeName
        + " is defined by both the program: "
        + getProgramOrigin()
        + " and the library: "
        + getLibraryOrigin();
  }
}
