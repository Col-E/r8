// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.bridge;

public class ResultImpl implements Result {

  private final String message;

  public ResultImpl(String message) {
    this.message = message;
  }

  @Override
  public void print() {
    System.out.println(message);
  }
}
