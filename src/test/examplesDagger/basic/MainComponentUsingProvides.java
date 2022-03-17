// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package basic;

import dagger.Component;
import javax.inject.Singleton;

@Component(modules = ModuleUsingProvides.class)
@Singleton
interface MainComponentUsingProvides extends MainComponent {}
