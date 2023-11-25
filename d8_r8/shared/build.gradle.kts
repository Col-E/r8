// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
}

tasks {

  val downloadDeps by registering(DownloadAllDependenciesTask::class) {
    this.setDependencies(getRoot(), allPublicDependencies())
  }

  val downloadDepsInternal by registering(DownloadAllDependenciesTask::class) {
    this.setDependencies(getRoot(), allInternalDependencies())
  }
}