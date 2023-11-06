// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public final class InvalidMappingFileException extends RuntimeException {

  public InvalidMappingFileException(Throwable throwable) {
    super(throwable);
  }

  @Override
  public String getMessage() {
    return "Unable to parse mapping file";
  }
}
