// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package properties

class UserDefinedProperty() {
    public var durationInMilliSeconds: Int = 0

    var durationInSeconds: Int
        get() = durationInMilliSeconds / 1000
        set(v) { durationInMilliSeconds = v * 1000 }
}

fun userDefinedProperty_noUseOfProperties() {
    UserDefinedProperty()
}

fun userDefinedProperty_useProperties() {
    val obj = UserDefinedProperty()
    obj.durationInSeconds = 5
    println(obj.durationInSeconds)
}