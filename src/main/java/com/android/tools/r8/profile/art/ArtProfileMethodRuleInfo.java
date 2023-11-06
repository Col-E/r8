// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public interface ArtProfileMethodRuleInfo {

  /** Returns true if this method rule method rule is flagged as hot ('H'). */
  boolean isHot();

  /** Returns true if this method rule method rule is flagged as startup ('S'). */
  boolean isStartup();

  /** Returns true if this method rule method rule is flagged as post-startup ('P'). */
  boolean isPostStartup();
}
