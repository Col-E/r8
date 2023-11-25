// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.origin;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/** Origin representing an entry in an archive. */
@KeepForApi
public class ArchiveEntryOrigin extends Origin {

  final String entryName;

  public ArchiveEntryOrigin(String entryName, Origin parent) {
    super(parent);
    this.entryName = entryName;
  }

  @Override
  public String part() {
    return entryName;
  }

  public String getEntryName() {
    return entryName;
  }
}
