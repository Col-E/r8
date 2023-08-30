// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

The examplesAndroidOLegacy source set is no longer included in build.gradle
after transitioning to gradle v8. If we need to update the project, one should
generate new class files and place in third_party/examplesAndroidOLegacy or
make a direct dependency on the generated jars.

See this commit where we check in the class files to get a sense of how to
compile the source sets:
https://r8-review.git.corp.google.com/c/r8/+/83161

