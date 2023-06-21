// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

/**
 * Base interface for android resources (resource table, manifest, res folder files)
 *
 * <p>Android resources are provided to R8 to allow for combined code and resource shrinking. This
 * allows us to reason about resources, and transitively other resources and code throughout the
 * entire compilation pipeline.
 */
@Keep
public interface AndroidResourceOutput extends Resource {
  // The path, within the app, of the resource.
  ResourcePath getPath();

  ByteDataView getByteDataView();
}
