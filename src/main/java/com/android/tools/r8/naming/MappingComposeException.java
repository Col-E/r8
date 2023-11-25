// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public class MappingComposeException extends Exception {

  public MappingComposeException(String message) {
    super(message);
  }
}
