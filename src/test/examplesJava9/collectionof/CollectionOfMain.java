// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package collectionof;

import java.util.List;
import java.util.Set;

public class CollectionOfMain {

  public static void main(String[] args) {
    try {
      System.out.println(Set.of("one").contains(null));
    } catch (NullPointerException npe) {
      System.out.println("npe");
    }
    try {
      System.out.println(List.of("one").contains(null));
    } catch (NullPointerException npe) {
      System.out.println("npe");
    }
  }
}
