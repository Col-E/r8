// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package basic;

import javax.inject.Inject;

// @Singleton (added by transformer in some tests)
public class I3Impl1 implements I3 {

  @Inject
  public I3Impl1() {}

  public String getName() {
    return "I3Impl1";
  }
}
