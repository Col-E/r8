// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public interface DataResourceProvider {

  @KeepForApi
  interface Visitor {
    void visit(DataDirectoryResource directory);
    void visit(DataEntryResource file);
  }

  void accept(Visitor visitor) throws ResourceException;

}
