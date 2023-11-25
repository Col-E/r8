// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidresources;

public class Resources {
  public static String GET_STRING_VALUE = "GET_STRING_VALUE";

  // Returns the GET_STRING_VALUE  to be able to distinguish resource inlined values from values
  // we get from this call (i.e., not inlined). Inlined values are the actual values from the
  // resource table.
  public String getString(int id) {
    return GET_STRING_VALUE;
  }

  public int getIdentifier(String name, String defType, String defPackage) {
    return 42;
  }
}
