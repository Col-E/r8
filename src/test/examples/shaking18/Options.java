// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package shaking18;

public class Options {
  // TODO(b/138913138): member value propagation can behave same with and without initialization.
  // public boolean alwaysFalse = false;
  public boolean alwaysFalse;
  public boolean dummy = false;

  public Options() {}
}
