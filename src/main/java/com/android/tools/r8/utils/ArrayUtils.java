// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

public class ArrayUtils {

  public static boolean containsInt(int[] array, int value) {
    for (int element : array) {
      if (element == value) {
        return true;
      }
    }
    return false;
  }

  /**
   * Copies the input array and then applies specified sparse changes.
   *
   * @param clazz target type's Class to cast
   * @param original an array of original elements
   * @param changedElements sparse changes to apply
   * @param <T> target type
   * @return a copy of original arrays while sparse changes are applied
   */
  public static <T> T[] copyWithSparseChanges(
      Class<T[]> clazz, T[] original, Map<Integer, T> changedElements) {
    T[] results = clazz.cast(Array.newInstance(clazz.getComponentType(), original.length));
    int pos = 0;
    for (Map.Entry<Integer, T> entry : changedElements.entrySet()) {
      int i = entry.getKey();
      System.arraycopy(original, pos, results, pos, i - pos);
      results[i] = entry.getValue();
      pos = i + 1;
    }
    if (pos < original.length) {
      System.arraycopy(original, pos, results, pos, original.length - pos);
    }
    return results;
  }

  public static <T> T[] filled(T[] array, T element) {
    Arrays.fill(array, element);
    return array;
  }

  public static <T> T[] initialize(T[] array, IntFunction<T> fn) {
    for (int i = 0; i < array.length; i++) {
      array[i] = fn.apply(i);
    }
    return array;
  }

  public static <T> boolean isEmpty(T[] array) {
    return array.length == 0;
  }

  public static boolean isSorted(int[] array) {
    for (int i = 0; i < array.length - 1; i++) {
      assert array[i] < array[i + 1];
    }
    return true;
  }

  public static int last(int[] array) {
    return array[array.length - 1];
  }

  public static <T> T last(T[] array) {
    return array[array.length - 1];
  }

  /**
   * Rewrites the input array based on the given function.
   *
   * @param original an array of original elements
   * @param mapper a mapper that rewrites an original element to a new one, maybe `null`
   * @param emptyArray an empty array
   * @return an array with written elements
   */
  @SuppressWarnings("unchecked")
  public static <S, T> T[] map(S[] original, Function<S, T> mapper, T[] emptyArray) {
    ArrayList<T> results = null;
    for (int i = 0; i < original.length; i++) {
      S oldOne = original[i];
      T newOne = mapper.apply(oldOne);
      if (newOne == oldOne) {
        if (results != null) {
          results.add((T) oldOne);
        }
      } else {
        if (results == null) {
          results = new ArrayList<>(original.length);
          for (int j = 0; j < i; j++) {
            results.add((T) original[j]);
          }
        }
        if (newOne != null) {
          results.add(newOne);
        }
      }
    }
    return results != null ? results.toArray(emptyArray) : (T[]) original;
  }

  /** Rewrites the input array to the output array unconditionally. */
  public static <T> String[] mapToStringArray(T[] original, Function<T, String> mapper) {
    String[] returnArr = new String[original.length];
    for (int i = 0; i < original.length; i++) {
      returnArr[i] = mapper.apply(original[i]);
    }
    return returnArr;
  }

  public static <T> T[] filter(T[] original, Predicate<T> predicate, T[] emptyArray) {
    return map(original, e -> predicate.test(e) ? e : null, emptyArray);
  }

  @SuppressWarnings("unchecked")
  public static <T> T[] filter(T[] original, Predicate<T> predicate, T[] emptyArray, int newSize) {
    T[] result = (T[]) Array.newInstance(emptyArray.getClass().getComponentType(), newSize);
    int newIndex = 0;
    for (int originalIndex = 0; originalIndex < original.length; originalIndex++) {
      T element = original[originalIndex];
      if (predicate.test(element)) {
        result[newIndex] = element;
        newIndex++;
      }
    }
    assert newIndex == newSize;
    return result;
  }

  public static int[] createIdentityArray(int size) {
    int[] array = new int[size];
    for (int i = 0; i < size; i++) {
      array[i] = i;
    }
    return array;
  }

  public static <T> boolean contains(T[] elements, T elementToLookFor) {
    for (Object element : elements) {
      if (Objects.equals(element, elementToLookFor)) {
        return true;
      }
    }
    return false;
  }

  public static <T, U> boolean contains(
      T[] elements, Function<T, U> elementMap, U mappedElementToLookFor) {
    for (T element : elements) {
      if (elementMap.apply(element).equals(mappedElementToLookFor)) {
        return true;
      }
    }
    return false;
  }

  public static int[] fromPredicate(IntPredicate predicate, int size) {
    int[] result = new int[size];
    for (int i = 0; i < size; i++) {
      result[i] = BooleanUtils.intValue(predicate.test(i));
    }
    return result;
  }

  public static void sumOfPredecessorsInclusive(int[] array) {
    for (int i = 1; i < array.length; i++) {
      array[i] += array[i - 1];
    }
  }

  /**
   * Copies the current array to a new array that can fit one more element and adds 'element' to
   * index |ts|. Only use this if adding a single element since copying the array is expensive.
   *
   * @param ts the original array
   * @param element the element to add
   * @return a new array with element on index |ts|
   */
  public static <T> T[] appendSingleElement(T[] ts, T element) {
    T[] newArray = Arrays.copyOf(ts, ts.length + 1);
    newArray[ts.length] = element;
    return newArray;
  }

  public static <T> T first(T[] ts) {
    return ts[0];
  }

  @SuppressWarnings("unchecked")
  public static <T> Optional<T>[] withOptionalNone(T[] ts) {
    Optional<T>[] optionals = new Optional[ts.length + 1];
    for (int i = 0; i < ts.length; i++) {
      optionals[i] = Optional.of(ts[i]);
    }
    optionals[ts.length] = Optional.empty();
    return optionals;
  }
}
