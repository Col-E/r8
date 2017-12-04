// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.position.Position;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

public class IOExceptionDiagnostic extends DiagnosticWithThrowable {

  private final Origin origin;
  private final String message;

  public IOExceptionDiagnostic(IOException e) {
    super(e);
    origin = extractOrigin(e);
    message = extractMessage(e);
  }

  public IOExceptionDiagnostic(IOException e, Origin origin) {
    super(e);
    this.origin = origin;
    message = extractMessage(e);
  }

  private String extractMessage(IOException e) {
    String message = e.getMessage();
    if (message == null || message.isEmpty()) {
      if (e instanceof NoSuchFileException || e instanceof FileNotFoundException) {
        message = "File not found";
      } else if (e instanceof FileAlreadyExistsException) {
        message = "File already exists";
      }
    }
    return message;
  }

  private Origin extractOrigin(IOException e) {
    Origin origin = Origin.unknown();

    if (e instanceof FileSystemException) {
      FileSystemException fse = (FileSystemException) e;
      if (fse.getFile() != null && !fse.getFile().isEmpty()) {
        origin = new PathOrigin(Paths.get(fse.getFile()));
      }
    }
    return origin;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    return message;
  }

}
