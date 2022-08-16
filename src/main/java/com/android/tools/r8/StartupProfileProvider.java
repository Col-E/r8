// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

/** Interface for providing a startup profile to the compiler. */
@FunctionalInterface
public interface StartupProfileProvider {

  /** Return the startup profile. */
  String get();
}
