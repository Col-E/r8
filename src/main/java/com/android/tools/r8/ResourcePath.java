// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

@Keep
public interface ResourcePath {

  // The location within the apk, bundle or resource directory, e.g., res/xml/foo.xml
  String location();
}
