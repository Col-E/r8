// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package collectiontoarray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

public class Main {
  public static void main(String[] args) {
    List<String> list = new ArrayList<>();
    list.add("one");
    list.add("two");
    // This default method was added in Android T.
    String[] toArray = list.toArray(String[]::new);
    System.out.println(Arrays.toString(toArray));

    List<String> myList = new MyList<>();
    myList.add("one");
    myList.add("two");
    // This default method was added in Android T.
    String[] toArray2 = myList.toArray(String[]::new);
    System.out.println(Arrays.toString(toArray2));
  }

  @SuppressWarnings("all")
  public static class MyList<T> extends ArrayList<T> {
    public <T> T[] toArray(IntFunction<T[]> generator) {
      System.out.println("Override");
      return super.toArray(generator);
    }
  }
}
