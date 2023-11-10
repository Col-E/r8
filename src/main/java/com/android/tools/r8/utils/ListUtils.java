// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class ListUtils {

  /**
   * Maps each element in the list using on the given function. Returns the mapped list if any
   * elements were rewritten, otherwise returns the original list.
   *
   * <p>If the given function {@param fn} returns null for an element {@code v}, this is interpreted
   * as the singleton list containing {@code v} (i.e., no changes should be made to the given
   * element).
   */
  public static <T> List<T> flatMapSameType(
      List<T> list, Function<T, Collection<T>> fn, List<T> defaultValue) {
    List<T> result = null;
    for (int i = 0; i < list.size(); i++) {
      T element = list.get(i);
      Collection<T> replacement = fn.apply(element);
      if (replacement == null) {
        if (result != null) {
          result.add(element);
        }
      } else {
        if (result == null) {
          result = new ArrayList<>(list.size() + replacement.size() - 1);
          for (int j = 0; j < i; j++) {
            result.add(list.get(j));
          }
        }
        result.addAll(replacement);
      }
    }
    return result != null ? result : defaultValue;
  }

  public static <S, T> List<T> flatMap(List<S> list, Function<S, Collection<T>> fn) {
    List<T> result = new ArrayList<>();
    list.forEach(element -> result.addAll(fn.apply(element)));
    return result;
  }

  @SuppressWarnings("unchecked")
  public static <S, T extends S> List<T> filter(
      Collection<S> list, Predicate<? super S> predicate) {
    ArrayList<T> filtered = new ArrayList<>(list.size());
    list.forEach(
        s -> {
          if (predicate.test(s)) {
            filtered.add((T) s);
          }
        });
    return filtered;
  }

  public static <T> T first(List<T> list) {
    return list.get(0);
  }

  public static <T> T firstMatching(List<T> list, Predicate<T> tester) {
    int i = firstIndexMatching(list, tester);
    return i >= 0 ? list.get(i) : null;
  }

  public static <T> int firstIndexMatching(List<T> list, Predicate<T> tester) {
    for (int i = 0; i < list.size(); i++) {
      if (tester.test(list.get(i))) {
        return i;
      }
    }
    return -1;
  }

  public static <T> T last(List<T> list) {
    return list.get(list.size() - 1);
  }

  public static <T> int lastIndexMatching(List<T> list, Predicate<T> tester) {
    for (int i = list.size() - 1; i >= 0; i--) {
      if (tester.test(list.get(i))) {
        return i;
      }
    }
    return -1;
  }

  public static <S, T> List<T> map(S[] list, Function<S, T> fn) {
    List<T> result = new ArrayList<>();
    for (S element : list) {
      result.add(fn.apply(element));
    }
    return result;
  }

  public static <S, T> List<T> map(Iterable<S> list, Function<S, T> fn) {
    List<T> result = new ArrayList<>();
    for (S element : list) {
      result.add(fn.apply(element));
    }
    return result;
  }

  public static <S, T> List<T> map(Collection<S> list, Function<S, T> fn) {
    List<T> result = new ArrayList<>(list.size());
    for (S element : list) {
      result.add(fn.apply(element));
    }
    return result;
  }

  public static <S, T> List<T> mapNotNull(Collection<S> list, Function<S, T> fn) {
    List<T> result = new ArrayList<>(list.size());
    for (S element : list) {
      T mapped = fn.apply(element);
      if (mapped != null) {
        result.add(mapped);
      }
    }
    return result;
  }

  /**
   * Rewrites the input list based on the given function. Returns the mapped list if any elements
   * were rewritten, otherwise returns defaultValue.
   */
  public static <T> List<T> mapOrElse(List<T> list, Function<T, T> fn, List<T> defaultValue) {
    return mapOrElse(list, (index, element) -> fn.apply(element), defaultValue);
  }

  /**
   * Rewrites the input list based on the given function. Returns the mapped list if any elements
   * were rewritten, otherwise returns defaultValue.
   */
  public static <T> List<T> mapOrElse(
      List<T> list, IntObjToObjFunction<T, T> fn, List<T> defaultValue) {
    ArrayList<T> result = null;
    for (int i = 0; i < list.size(); i++) {
      T oldElement = list.get(i);
      T newElement = fn.apply(i, oldElement);
      if (newElement == oldElement) {
        if (result != null) {
          result.add(oldElement);
        }
      } else {
        if (result == null) {
          result = new ArrayList<>(list.size());
          for (int j = 0; j < i; j++) {
            result.add(list.get(j));
          }
        }
        if (newElement != null) {
          result.add(newElement);
        }
      }
    }
    return result != null ? result : defaultValue;
  }

  /**
   * Rewrites the input list based on the given function. Returns the mapped list if any elements
   * were rewritten, otherwise returns the original list.
   */
  public static <T> List<T> mapOrElse(List<T> list, Function<T, T> fn) {
    return mapOrElse(list, fn, list);
  }

  /**
   * Takes elements from the input list depending on the predicate being true. Returns the filtered
   * list otherwise returns the original list of none were removed.
   */
  public static <T> List<T> filterOrElse(List<T> list, Predicate<T> predicate) {
    return mapOrElse(list, element -> predicate.test(element) ? element : null, list);
  }

  public static <T> ArrayList<T> newArrayList(T element) {
    ArrayList<T> list = new ArrayList<>(1);
    list.add(element);
    return list;
  }

  public static <T> ArrayList<T> newArrayList(T element, T other) {
    ArrayList<T> list = new ArrayList<>(2);
    list.add(element);
    list.add(other);
    return list;
  }

  public static <T> ArrayList<T> newArrayList(ForEachable<T> forEachable) {
    ArrayList<T> list = new ArrayList<>();
    forEachable.forEach(list::add);
    return list;
  }

  public static <T> ArrayList<T> newInitializedArrayList(int size, T element) {
    ArrayList<T> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(element);
    }
    return list;
  }

  public static <T> ImmutableList<T> newImmutableList(ForEachable<T> forEachable) {
    ImmutableList.Builder<T> builder = ImmutableList.builder();
    forEachable.forEach(builder::add);
    return builder.build();
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  public static <T> LinkedList<T> newLinkedList(T element) {
    LinkedList<T> list = new LinkedList<>();
    list.add(element);
    return list;
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  public static <T> LinkedList<T> newLinkedList(ForEachable<T> forEachable) {
    LinkedList<T> list = new LinkedList<>();
    forEachable.forEach(list::add);
    return list;
  }

  public static <T> Optional<T> removeFirstMatch(List<T> list, Predicate<T> element) {
    int index = firstIndexMatching(list, element);
    if (index >= 0) {
      return Optional.of(list.remove(index));
    }
    return Optional.empty();
  }

  public static <T> T removeLast(List<T> list) {
    return list.remove(list.size() - 1);
  }

  public static <T> List<T> reverse(List<T> list) {
    List<T> reversed = new ArrayList<>(list.size());
    for (int i = list.size() - 1; i >= 0; i--) {
      reversed.add(list.get(i));
    }
    return reversed;
  }

  public static <T extends Comparable<T>> boolean verifyListIsOrdered(List<T> list) {
    for (int i = list.size() - 1; i > 0; i--) {
      if (list.get(i).compareTo(list.get(i - 1)) < 0) {
        return false;
      }
    }
    return true;
  }

  public static <T, R> R fold(Collection<T> items, R identity, BiFunction<R, T, R> acc) {
    R result = identity;
    for (T item : items) {
      result = acc.apply(result, item);
    }
    return result;
  }

  public static <T> void forEachWithIndex(List<T> items, ReferenceAndIntConsumer<T> consumer) {
    for (int i = 0; i < items.size(); i++) {
      consumer.accept(items.get(i), i);
    }
  }

  public static MappedRange lastOrNull(List<MappedRange> existingMappedRanges) {
    return existingMappedRanges == null ? null : last(existingMappedRanges);
  }

  public interface ReferenceAndIntConsumer<T> {
    void accept(T item, int index);
  }

  public static <T> List<T> sort(Collection<T> items, Comparator<T> comparator) {
    List<T> sorted = new ArrayList<>(items);
    sorted.sort(comparator);
    return sorted;
  }

  public static <T> void destructiveSort(List<T> items, Comparator<T> comparator) {
    items.sort(comparator);
  }

  // Utility to add a slow verification of a comparator as part of sorting. Note that this
  // should not generally be used in asserts unless the quadratic behavior can be tolerated.
  public static <T> List<T> sortAndVerify(List<T> items, Comparator<T> comparator) {
    List<T> sorted = sort(items, comparator);
    assert verifyComparatorOnSortedList(sorted, comparator);
    return sorted;
  }

  // Utility to add a slow verification of a comparator as part of sorting. Note that this
  // should not generally be used in asserts unless the quadratic behavior can be tolerated.
  public static <T> void destructiveSortAndVerify(List<T> items, Comparator<T> comparator) {
    destructiveSort(items, comparator);
    assert verifyComparatorOnSortedList(items, comparator);
  }

  public static <T> int uniqueIndexMatching(List<T> list, Predicate<T> predicate) {
    int result = -1;
    for (int i = 0; i < list.size(); i++) {
      T element = list.get(i);
      if (predicate.test(element)) {
        if (result == -1) {
          result = i;
        } else {
          return -1;
        }
      }
    }
    return result;
  }

  private static <T> boolean verifyComparatorOnSortedList(List<T> items, Comparator<T> comparator) {
    for (int i = 0; i < items.size(); i++) {
      boolean allowEqual = true;
      for (int j = i; j < items.size(); j++) {
        T a = items.get(i);
        T b = items.get(j);
        int result1 = comparator.compare(a, b);
        int result2 = comparator.compare(b, a);
        boolean isEqual = result1 == 0 && result2 == 0;
        if (i == j) {
          assert isEqual;
        } else if (!allowEqual || !isEqual) {
          allowEqual = false;
          assert result1 < 0;
          assert result2 > 0;
        }
      }
    }
    return true;
  }

  public static <T> List<T> joinNewArrayList(List<T> one, List<T> other) {
    ArrayList<T> ts = new ArrayList<>(one.size() + other.size());
    ts.addAll(one);
    ts.addAll(other);
    return ts;
  }

  public static <T> List<T> unmodifiableForTesting(List<T> list) {
    return InternalOptions.assertionsEnabled() ? Collections.unmodifiableList(list) : list;
  }
}
