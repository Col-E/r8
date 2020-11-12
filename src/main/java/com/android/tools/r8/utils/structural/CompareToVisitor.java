// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import java.util.Comparator;

/** Base class for a visitor implementing compareTo on a structural item. */
public abstract class CompareToVisitor {

  public abstract void visitBool(boolean value1, boolean value2);

  public abstract void visitInt(int value1, int value2);

  public abstract void visitDexString(
      DexString string1, DexString string2, Comparator<DexString> comparator);

  public abstract void visitDexType(DexType type1, DexType type2);

  public abstract void visitDexTypeList(DexTypeList types1, DexTypeList types2);

  public void visitDexField(DexField field1, DexField field2) {
    visit(field1, field2, field1.getStructuralAccept());
  }

  public void visitDexMethod(DexMethod method1, DexMethod method2) {
    visit(method1, method2, method1.getStructuralAccept());
  }

  public abstract <S> void visit(S item1, S item2, StructuralAccept<S> accept);

  @Deprecated
  public abstract <S> void visit(S item1, S item2, Comparator<S> comparator);
}
