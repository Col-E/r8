// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;
import com.android.tools.r8.utils.structural.StructuralItem.HashingAccept;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public class CompareToVisitorWithNamingLens extends CompareToVisitor {

  public static <T> int run(T item1, T item2, NamingLens namingLens, StructuralAccept<T> visit) {
    return run(item1, item2, namingLens, (i1, i2, visitor) -> visitor.visit(i1, i2, visit));
  }

  public static <T> int run(
      T item1, T item2, NamingLens namingLens, CompareToAccept<T> compareToAccept) {
    CompareToVisitorWithNamingLens state = new CompareToVisitorWithNamingLens(namingLens);
    compareToAccept.accept(item1, item2, state);
    return state.order;
  }

  private final NamingLens namingLens;
  private int order = 0;

  public CompareToVisitorWithNamingLens(NamingLens namingLens) {
    this.namingLens = namingLens;
  }

  @Override
  public void visitBool(boolean value1, boolean value2) {
    if (order == 0) {
      order = Boolean.compare(value1, value2);
    }
  }

  @Override
  public void visitInt(int value1, int value2) {
    if (order == 0) {
      order = Integer.compare(value1, value2);
    }
  }

  @Override
  public <S> void visit(S item1, S item2, Comparator<S> comparator) {
    if (order == 0) {
      order = comparator.compare(item1, item2);
    }
  }

  @Override
  public <S> void visit(S item1, S item2, StructuralAccept<S> accept) {
    if (order == 0) {
      accept.accept(new ItemSpecification<>(item1, item2, this));
    }
  }

  @Override
  public void visitDexString(
      DexString string1, DexString string2, Comparator<DexString> comparator) {
    if (order == 0) {
      order = comparator.compare(string1, string2);
    }
  }

  @Override
  public void visitDexType(DexType type1, DexType type2) {
    if (order == 0) {
      namingLens.lookupDescriptor(type1).acceptCompareTo(namingLens.lookupDescriptor(type2), this);
    }
  }

  @Override
  public void visitDexField(DexField field1, DexField field2) {
    if (order == 0) {
      field1.holder.acceptCompareTo(field2.holder, this);
      if (order == 0) {
        namingLens.lookupName(field1).acceptCompareTo(namingLens.lookupName(field2), this);
        if (order == 0) {
          field1.type.acceptCompareTo(field2.type, this);
        }
      }
    }
  }

  @Override
  public void visitDexMethod(DexMethod method1, DexMethod method2) {
    if (order == 0) {
      method1.holder.acceptCompareTo(method2.holder, this);
      if (order == 0) {
        namingLens.lookupName(method1).acceptCompareTo(namingLens.lookupName(method2), this);
        if (order == 0) {
          method1.proto.acceptCompareTo(method2.proto, this);
        }
      }
    }
  }

  @Override
  public void visitDexTypeList(DexTypeList types1, DexTypeList types2) {
    // Comparison is lexicographic with comparisons between items prior to the length of the lists.
    if (order == 0) {
      int length = Math.min(types1.size(), types2.size());
      for (int i = 0; i < length && order == 0; i++) {
        visitDexType(types1.values[i], types2.values[i]);
      }
      if (order == 0) {
        visitInt(types1.size(), types2.size());
      }
    }
  }

  private static class ItemSpecification<T>
      extends StructuralSpecification<T, ItemSpecification<T>> {

    private final CompareToVisitorWithNamingLens parent;
    private final T item1;
    private final T item2;

    private ItemSpecification(T item1, T item2, CompareToVisitorWithNamingLens parent) {
      this.item1 = item1;
      this.item2 = item2;
      this.parent = parent;
    }

    @Override
    public ItemSpecification<T> withAssert(Predicate<T> predicate) {
      assert predicate.test(item1);
      assert predicate.test(item2);
      return this;
    }

    @Override
    public ItemSpecification<T> withBool(Predicate<T> getter) {
      parent.visitBool(getter.test(item1), getter.test(item2));
      return this;
    }

    @Override
    public ItemSpecification<T> withInt(ToIntFunction<T> getter) {
      parent.visitInt(getter.applyAsInt(item1), getter.applyAsInt(item2));
      return this;
    }

    @Override
    public <S> ItemSpecification<T> withConditionalCustomItem(
        Predicate<T> predicate,
        Function<T, S> getter,
        CompareToAccept<S> compare,
        HashingAccept<S> hasher) {
      if (parent.order == 0) {
        boolean test1 = predicate.test(item1);
        boolean test2 = predicate.test(item2);
        if (test1 && test2) {
          compare.accept(getter.apply(item1), getter.apply(item2), parent);
        } else {
          parent.visitBool(test1, test2);
        }
      }
      return this;
    }
  }
}
