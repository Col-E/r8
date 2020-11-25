// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package sealed;

public sealed abstract class Compiler permits R8Compiler, D8Compiler {

  public abstract void run();
}