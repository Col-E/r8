// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.file;

public enum StandardOpenOption implements OpenOption {
  READ,
  WRITE,
  APPEND,
  TRUNCATE_EXISTING,
  CREATE,
  CREATE_NEW,
  DELETE_ON_CLOSE,
  SPARSE,
  SYNC,
  DSYNC;
}
