// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.UseRegistry;
import java.util.ListIterator;
import org.objectweb.asm.Opcodes;

public class CfInstanceFieldWrite extends CfFieldInstruction {

  public CfInstanceFieldWrite(DexField field) {
    this(field, field);
  }

  public CfInstanceFieldWrite(DexField field, DexField declaringField) {
    super(Opcodes.PUTFIELD, field, declaringField);
  }

  @Override
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerInstanceFieldWrite(getField());
  }
}
