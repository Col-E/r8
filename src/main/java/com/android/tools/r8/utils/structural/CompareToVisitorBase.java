// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;
import com.android.tools.r8.utils.structural.StructuralItem.HashingAccept;
import java.util.Comparator;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/** Base class to share most visiting methods */
public abstract class CompareToVisitorBase extends CompareToVisitor {

  private static boolean DEBUG = false;

  // Helper to debug insert a breakpoint on order values.
  public static int debug(int order) {
    if (DEBUG && order != 0) {
      return order;
    }
    return order;
  }

  @Override
  public final int visitBool(boolean value1, boolean value2) {
    return debug(Boolean.compare(value1, value2));
  }

  @Override
  public final int visitInt(int value1, int value2) {
    return debug(Integer.compare(value1, value2));
  }

  @Override
  public int visitLong(long value1, long value2) {
    return debug(Long.compare(value1, value2));
  }

  @Override
  public int visitFloat(float value1, float value2) {
    return debug(Float.compare(value1, value2));
  }

  @Override
  public int visitDouble(double value1, double value2) {
    return debug(Double.compare(value1, value2));
  }

  @Override
  public <S> int visitItemIterator(
      Iterator<S> it1, Iterator<S> it2, CompareToAccept<S> compareToAccept) {
    int order = 0;
    while (order == 0 && it1.hasNext() && it2.hasNext()) {
      order = compareToAccept.acceptCompareTo(it1.next(), it2.next(), this);
    }
    if (order == 0) {
      order = visitBool(it1.hasNext(), it2.hasNext());
    }
    return debug(order);
  }

  @Override
  public int visitDexString(DexString string1, DexString string2) {
    return debug(string1.compareTo(string2));
  }

  @Override
  public int visitDexReference(DexReference reference1, DexReference reference2) {
    int order = visitInt(reference1.referenceTypeOrder(), reference2.referenceTypeOrder());
    if (order == 0) {
      assert reference1.getClass() == reference2.getClass();
      if (reference1.isDexType()) {
        order = visitDexType(reference1.asDexType(), reference2.asDexType());
      } else if (reference1.isDexField()) {
        order = visitDexField(reference1.asDexField(), reference2.asDexField());
      } else {
        order = visitDexMethod(reference1.asDexMethod(), reference2.asDexMethod());
      }
    }
    return debug(order);
  }

  @Override
  public final <S> int visit(S item1, S item2, Comparator<S> comparator) {
    return debug(comparator.compare(item1, item2));
  }

  @Override
  public final <S> int visit(S item1, S item2, StructuralMapping<S> accept) {
    ItemSpecification<S> itemVisitor = new ItemSpecification<>(item1, item2, this);
    accept.apply(itemVisitor);
    return debug(itemVisitor.order);
  }

  private static class ItemSpecification<T>
      extends StructuralSpecification<T, ItemSpecification<T>> {

    private final CompareToVisitorBase parent;
    private final T item1;
    private final T item2;
    private int order = 0;

    private ItemSpecification(T item1, T item2, CompareToVisitorBase parent) {
      this.item1 = item1;
      this.item2 = item2;
      this.parent = parent;
    }

    @Override
    ItemSpecification<T> self() {
      return this;
    }

    @Override
    public ItemSpecification<T> withAssert(Predicate<T> predicate) {
      assert predicate.test(item1);
      assert predicate.test(item2);
      return this;
    }

    @Override
    public ItemSpecification<T> withBool(Predicate<T> getter) {
      if (order == 0) {
        order = parent.visitBool(getter.test(item1), getter.test(item2));
      }
      return this;
    }

    @Override
    public ItemSpecification<T> withInt(ToIntFunction<T> getter) {
      if (order == 0) {
        order = parent.visitInt(getter.applyAsInt(item1), getter.applyAsInt(item2));
      }
      return this;
    }

    @Override
    public ItemSpecification<T> withLong(ToLongFunction<T> getter) {
      if (order == 0) {
        order = parent.visitLong(getter.applyAsLong(item1), getter.applyAsLong(item2));
      }
      return this;
    }

    @Override
    public ItemSpecification<T> withDouble(ToDoubleFunction<T> getter) {
      if (order == 0) {
        order = parent.visitDouble(getter.applyAsDouble(item1), getter.applyAsDouble(item2));
      }
      return this;
    }

    @Override
    public ItemSpecification<T> withIntArray(Function<T, int[]> getter) {
      if (order == 0) {
        int[] is1 = getter.apply(item1);
        int[] is2 = getter.apply(item2);
        int minLength = Math.min(is1.length, is2.length);
        for (int i = 0; i < minLength && order == 0; i++) {
          order = parent.visitInt(is1[i], is2[i]);
        }
        if (order == 0) {
          order = parent.visitInt(is1.length, is2.length);
        }
      }
      return this;
    }

    @Override
    public ItemSpecification<T> withByteArray(Function<T, byte[]> getter) {
      if (order == 0) {
        byte[] is1 = getter.apply(item1);
        byte[] is2 = getter.apply(item2);
        int minLength = Math.min(is1.length, is2.length);
        for (int i = 0; i < minLength && order == 0; i++) {
          order = parent.visitInt(is1[i], is2[i]);
        }
        if (order == 0) {
          order = parent.visitInt(is1.length, is2.length);
        }
      }
      return this;
    }

    @Override
    public ItemSpecification<T> withShortArray(Function<T, short[]> getter) {
      if (order == 0) {
        short[] is1 = getter.apply(item1);
        short[] is2 = getter.apply(item2);
        int minLength = Math.min(is1.length, is2.length);
        for (int i = 0; i < minLength && order == 0; i++) {
          order = parent.visitInt(is1[i], is2[i]);
        }
        if (order == 0) {
          order = parent.visitInt(is1.length, is2.length);
        }
      }
      return this;
    }

    @Override
    public <S> ItemSpecification<T> withConditionalCustomItem(
        Predicate<T> predicate,
        Function<T, S> getter,
        CompareToAccept<S> compare,
        HashingAccept<S> hasher) {
      if (order == 0) {
        boolean test1 = predicate.test(item1);
        boolean test2 = predicate.test(item2);
        if (test1 && test2) {
          order = compare.acceptCompareTo(getter.apply(item1), getter.apply(item2), parent);
        } else {
          order = parent.visitBool(test1, test2);
        }
      }
      return this;
    }

    @Override
    protected <S> ItemSpecification<T> withCustomItemIterator(
        Function<T, Iterator<S>> getter, CompareToAccept<S> compare, HashingAccept<S> hasher) {
      if (order == 0) {
        order = parent.visitItemIterator(getter.apply(item1), getter.apply(item2), compare);
      }
      return this;
    }

    @Override
    public ItemSpecification<T> withDexReference(Function<T, DexReference> getter) {
      if (order == 0) {
        order = parent.visitDexReference(getter.apply(item1), getter.apply(item2));
      }
      return this;
    }
  }
}
