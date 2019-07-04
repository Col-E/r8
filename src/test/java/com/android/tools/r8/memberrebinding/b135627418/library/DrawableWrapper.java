// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding.b135627418.library;

// android.graphics.drawable.DrawableWrapper
public class DrawableWrapper extends Drawable {
  public void setAlpha(int alpha) {
    System.out.println("In DrawableWrapper.setAlpha");
  }
}
