#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

try:
  import gi
  gi.require_version('Notify', '0.7')
  from gi.repository import Notify
  Notify.init('R8 build tools')

  def notify(message):
    try:
      Notify.Notification.new('R8 build tools', message).show()
    except:
      return

except ImportError:
  def notify(message):
    return
