// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For a given collection of inputs, returns the non overlapping intervals so that the collection
 * would be split in N sub-collections.
 */
public class Jdk11TestInputSplitter {

  private static int splitStart(int length, int index, int split) {
    assert index >= 0 && index < split;
    return index * length / split;
  }

  private static int splitEnd(int length, int index, int split) {
    assert index >= 0 && index < split;
    return (index + 1) * length / split;
  }

  static Map<String, String> split(Map<String, String> input, int index, int split) {
    int start = splitStart(input.size(), index, split);
    int end = splitEnd(input.size(), index, split);
    List<String> keys = new ArrayList<>(input.keySet());
    keys.sort(Comparator.naturalOrder());
    List<String> splitList = keys.subList(start, end);
    Map<String, String> newMap = new HashMap<>();
    for (String key : splitList) {
      newMap.put(key, input.get(key));
    }
    return newMap;
  }

  static String[] split(String[] input, int index, int split) {
    int start = splitStart(input.length, index, split);
    int end = splitEnd(input.length, index, split);
    Arrays.sort(input);
    return Arrays.copyOfRange(input, start, end);
  }
}
