// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.google.common.hash.Hasher;
import java.util.function.BiConsumer;

public abstract class HashingVisitor {

  public abstract void visitBool(boolean value);

  public abstract void visitInt(int value);

  public abstract void visitDexString(DexString string);

  public abstract void visitDexType(DexType type);

  public abstract void visitDexTypeList(DexTypeList types);

  public abstract <S> void visit(S item, StructuralAccept<S> accept);

  @Deprecated
  public abstract <S> void visit(S item, BiConsumer<S, Hasher> hasher);
}
