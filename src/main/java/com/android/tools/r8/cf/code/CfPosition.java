// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.ir.code.Position;
import org.objectweb.asm.MethodVisitor;

public class CfPosition extends CfInstruction {

  private final CfLabel label;
  private final Position position;

  public CfPosition(CfLabel label, Position position) {
    this.label = label;
    this.position = position;
  }

  @Override
  public void write(MethodVisitor visitor) {
    visitor.visitLineNumber(position.line, label.getLabel());
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  public Position getPosition() {
    return position;
  }
}
