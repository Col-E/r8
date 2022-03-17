// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package basic;

import dagger.Module;
import dagger.Provides;

@Module
class ModuleUsingProvides {
  @Provides
  // @Singleton (added by transformer in some tests)
  public static I1 i1() {
    return new I1Impl2();
  }

  @Provides
  // @Singleton (added by transformer in some tests)
  public static I2 i2() {
    return new I2Impl2();
  }

  @Provides
  // @Singleton (added by transformer in some tests)
  public static I3 i3() {
    return new I3Impl2();
  }
}
