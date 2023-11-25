// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public class IllegalClassNameLookupException extends RuntimeException {

  private final String typeName;

  public IllegalClassNameLookupException(String typeName) {
    this.typeName = typeName;
  }

  @Override
  public String getMessage() {
    return "Illegal lookup of " + typeName + ".";
  }
}
