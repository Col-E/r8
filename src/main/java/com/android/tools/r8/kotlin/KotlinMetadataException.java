// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

/** Exception class to ensure that exceptions arising from kotlin metadata parsing are handled */
public class KotlinMetadataException extends Exception {

  KotlinMetadataException() {}

  KotlinMetadataException(Throwable cause) {
    super(cause);
  }
}
