// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
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
  public static <T> List<T> flatMap(
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
    ArrayList<T> result = null;
    for (int i = 0; i < list.size(); i++) {
      T oldElement = list.get(i);
      T newElement = fn.apply(oldElement);
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

  public static <T> ArrayList<T> newArrayList(T element) {
    ArrayList<T> list = new ArrayList<>();
    list.add(element);
    return list;
  }

  public static <T> ArrayList<T> newArrayList(ForEachable<T> forEachable) {
    ArrayList<T> list = new ArrayList<>();
    forEachable.forEach(list::add);
    return list;
  }

  public static <T> ImmutableList<T> newImmutableList(ForEachable<T> forEachable) {
    ImmutableList.Builder<T> builder = ImmutableList.builder();
    forEachable.forEach(builder::add);
    return builder.build();
  }

  public static <T> LinkedList<T> newLinkedList(T element) {
    LinkedList<T> list = new LinkedList<>();
    list.add(element);
    return list;
  }

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

  public interface ReferenceAndIntConsumer<T> {
    void accept(T item, int index);
  }
}
