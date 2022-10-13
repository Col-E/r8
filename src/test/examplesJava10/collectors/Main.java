// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package collectors;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

  public static void main(String[] args) {
    Collector<Object, ?, List<Object>> filtering =
        Collectors.filtering(Objects::nonNull, Collectors.toList());
    System.out.println(Stream.of(null, 1).collect(filtering).get(0));

    Collector<List<?>, ?, List<Object>> collector =
        Collectors.flatMapping(Collection::stream, Collectors.toList());
    System.out.println(Stream.of(List.of(1)).collect(collector).get(0));

    Collector<Object, ?, List<Object>> toList = Collectors.toUnmodifiableList();
    System.out.println(Stream.of(1).collect(toList).get(0));
    Collector<Object, ?, Set<Object>> toSet = Collectors.toUnmodifiableSet();
    System.out.println(Stream.of(1).collect(toSet).iterator().next());
    Collector<Object, ?, Map<String, Integer>> toMap1 =
        Collectors.toUnmodifiableMap(Object::toString, Object::hashCode);
    System.out.println(Stream.of(1).collect(toMap1).keySet().iterator().next());
    Collector<Object, ?, Map<String, Integer>> toMap2 =
        Collectors.toUnmodifiableMap(Object::toString, Object::hashCode, (x, y) -> x);
    System.out.println(Stream.of(1).collect(toMap2).keySet().iterator().next());
  }
}
