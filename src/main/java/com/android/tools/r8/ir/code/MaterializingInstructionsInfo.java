// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.ir.analysis.type.TypeElement;

public interface MaterializingInstructionsInfo {

  DebugLocalInfo getLocalInfo();

  TypeElement getOutType();

  Position getPosition();

  static MaterializingInstructionsInfo create(
      TypeElement type, DebugLocalInfo local, Position position) {
    return new MaterializingInstructionsInfo() {

      @Override
      public DebugLocalInfo getLocalInfo() {
        return local;
      }

      @Override
      public TypeElement getOutType() {
        return type;
      }

      @Override
      public Position getPosition() {
        return position;
      }
    };
  }
}
