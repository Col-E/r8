// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaringwithandroidjar25;

import java.util.Comparator;

public class StaticMethodInAndroidJar25 {
  public static void main(String[] args) throws Exception {
    Comparator<String> comparing =
        Comparator.comparing(x -> x, String::compareTo);
    System.out.println("'a' <> 'b' = " + comparing.compare("a", "b"));
    System.out.println("'b' <> 'b' = " + comparing.compare("b", "b"));
    System.out.println("'c' <> 'b' = " + comparing.compare("c", "b"));
  }
}
