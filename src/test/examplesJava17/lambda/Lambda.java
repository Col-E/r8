// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambda;

import java.util.ArrayList;
import java.util.List;

public class Lambda {

  interface StringPredicate {

    boolean test(String t);

    default StringPredicate or(StringPredicate other) {
      return (t) -> test(t) || other.test(t);
    }
  }

  public static void main(String[] args) {
    ArrayList<String> strings = new ArrayList<>();
    strings.add("abc");
    strings.add("abb");
    strings.add("bbc");
    strings.add("aac");
    strings.add("acc");
    StringPredicate aaStart = Lambda::aaStart;
    StringPredicate bbNot = Lambda::bbNot;
    StringPredicate full = aaStart.or(bbNot);
    for (String string : ((List<String>) strings.clone())) {
      if (full.test(string)) {
        strings.remove(string);
      }
    }
    System.out.println(strings);
  }

  private static boolean aaStart(String str) {
    return str.startsWith("aa");
  }

  private static boolean bbNot(String str) {
    return !str.contains("bb");
  }
}
