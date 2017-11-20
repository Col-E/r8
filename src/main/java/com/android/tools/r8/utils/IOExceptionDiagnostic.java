// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.Location;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

public class IOExceptionDiagnostic extends DiagnosticWithThrowable {

  private final Location location;
  private final String message;

  public IOExceptionDiagnostic(IOException e) {
    super(e);
    Location location = Location.UNKNOWN;

    if (e instanceof FileSystemException) {
      FileSystemException fse = (FileSystemException) e;
      if (fse.getFile() != null && !fse.getFile().isEmpty()) {
        location = new Location(new PathOrigin(Paths.get(fse.getFile())));
      }
    }

    String message = e.getMessage();
    if (message == null || message.isEmpty()) {
      if (e instanceof NoSuchFileException || e instanceof FileNotFoundException) {
        message = "File not found";
      } else if (e instanceof FileAlreadyExistsException) {
        message = "File already exists";
      }
    }

    this.location = location;
    this.message = message;

  }

  @Override
  public Location getLocation() {
    return location;
  }

  @Override
  public String getDiagnosticMessage() {
    return message;
  }

}
