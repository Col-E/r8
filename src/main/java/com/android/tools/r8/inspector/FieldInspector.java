// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.inspector;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.FieldReference;
import java.util.Optional;

/** Inspector for a field definition. */
@KeepForApi
public interface FieldInspector {

  /** Get the field reference for the field of this inspector. */
  FieldReference getFieldReference();

  /** True if the field is declared static. */
  boolean isStatic();

  /** True if the field is declared final. */
  boolean isFinal();

  /**
   * Returns an inspector for the initial value if it is known by the compiler.
   *
   * <p>Note that the determination of the value is best effort, often the value will simply be the
   * default value for the given type.
   */
  Optional<ValueInspector> getInitialValue();
}
