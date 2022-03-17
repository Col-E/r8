// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package basic;

import dagger.Binds;
import dagger.Module;

@Module
interface ModuleUsingBinds {
  @Binds
  I1 i1(I1Impl1 i1);

  @Binds
  I2 i2(I2Impl1 i2);

  @Binds
  I3 i3(I3Impl1 i3);
}
