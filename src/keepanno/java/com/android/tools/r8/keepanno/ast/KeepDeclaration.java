// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

/** Base class for the declarations represented in the keep annoations library. */
public abstract class KeepDeclaration {

  public final boolean isKeepEdge() {
    return asKeepEdge() != null;
  }

  public KeepEdge asKeepEdge() {
    return null;
  }

  public final boolean isKeepCheck() {
    return asKeepCheck() != null;
  }

  public KeepCheck asKeepCheck() {
    return null;
  }
}
