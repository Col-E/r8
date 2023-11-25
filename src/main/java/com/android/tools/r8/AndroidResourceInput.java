// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.io.InputStream;

/**
 * Base interface for android resources (resource table, manifest, res folder files)
 *
 * <p>Android resources are provided to R8 to allow for combined code and resource shrinking. This
 * allows us to reason about resources, and transitively other resources and code throughout the
 * entire compilation pipeline.
 */
@KeepForApi
public interface AndroidResourceInput extends Resource {

  @KeepForApi
  enum Kind {
    // The AndroidManifest.xml file in proto format.
    MANIFEST,
    // The resource table, in proto format.
    RESOURCE_TABLE,
    // An xml file within the res folder, in proto format if not inside res/raw, otherwise
    // in UTF-8 format.
    XML_FILE,
    // Any other binary file withing the res folder.
    RES_FOLDER_FILE,
    // Other files are ignored, but copied through
    UNKNOWN
  }

  // The path, within the app, of the resource.
  ResourcePath getPath();

  Kind getKind();

  InputStream getByteStream() throws ResourceException;
}
