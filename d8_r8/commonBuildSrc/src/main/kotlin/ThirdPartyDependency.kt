// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File

enum class DependencyType {
  GOOGLE_STORAGE,
  X20
}

data class ThirdPartyDependency(
  val packageName : String,
  val path : File,
  val sha1File : File,
  val type: DependencyType = DependencyType.GOOGLE_STORAGE)
