// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package usestdlib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class UseStdLib {

  public static void main(String[] args) {
    List<String> myStrings = new ArrayList<>();
    Collections.addAll(myStrings, args);
    myStrings.stream().forEach(new Consumer<String>() {
      @Override
      public void accept(String s) {
        System.out.println(s);
      }
    });
  }
}
