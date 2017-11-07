// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public class CfLabel extends CfInstruction {

  private Label label = null;

  public Label getLabel() {
    if (label == null) {
      label = new Label();
    }
    return label;
  }

  @Override
  public void write(MethodVisitor visitor) {
    visitor.visitLabel(getLabel());
  }
}
