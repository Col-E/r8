// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.Opcodes;

public class OneWord extends SingletonFrameType implements SingleFrameType {

  static final OneWord SINGLETON = new OneWord();

  private OneWord() {}

  @Override
  public boolean isOneWord() {
    return true;
  }

  @Override
  public SingleFrameType asSingle() {
    return this;
  }

  @Override
  public SingleFrameType join(
      AppView<? extends AppInfoWithClassHierarchy> appView, SingleFrameType frameType) {
    return this;
  }

  @Override
  public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
    return Opcodes.TOP;
  }

  @Override
  public String toString() {
    return "oneword";
  }
}
