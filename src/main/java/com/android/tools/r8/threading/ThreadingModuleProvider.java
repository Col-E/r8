// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.threading;

import com.android.tools.r8.Keep;

/**
 * Interface to obtain a threading module.
 *
 * <p>The provider is loaded via Java service loader so its interface must be kept.
 */
@Keep
public interface ThreadingModuleProvider {

  ThreadingModule create();
}
