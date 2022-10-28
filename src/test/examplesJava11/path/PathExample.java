// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package path;

import java.nio.file.Path;

public class PathExample {

  public static void main(String[] args) {
    Path thePath = Path.of("foo", "bar");
    System.out.println(thePath);
    System.out.println(Path.of(thePath.toUri()).getFileName());
  }
}
