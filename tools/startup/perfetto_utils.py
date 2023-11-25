#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import subprocess
import sys


def get_trace_processor():
    try:
        from perfetto.trace_processor import TraceProcessor
    except ImportError:
        sys.exit(
            'Unable to analyze perfetto trace without the perfetto library. '
            'Install instructions:\n'
            '    sudo apt install python3-pip\n'
            '    pip3 install perfetto')
    return TraceProcessor


def ensure_record_android_trace(tmp_dir):
    record_android_trace_path = os.path.join(tmp_dir, 'record_android_trace')
    if not os.path.exists(record_android_trace_path):
        cmd = [
            'curl', '--output', record_android_trace_path, '--silent',
            'https://raw.githubusercontent.com/google/perfetto/master/tools/'
            'record_android_trace'
        ]
        subprocess.check_call(cmd)
        assert os.path.exists(record_android_trace_path)
    return record_android_trace_path


def record_android_trace(out_dir, tmp_dir, device_id=None):
    record_android_trace_path = ensure_record_android_trace(tmp_dir)
    config_path = os.path.join(os.path.dirname(__file__), 'config.pbtx')
    perfetto_trace_path = os.path.join(out_dir, 'trace.perfetto-trace')
    cmd = [
        sys.executable, record_android_trace_path, '--config', config_path,
        '--out', perfetto_trace_path, '--no-open'
    ]
    if device_id is not None:
        cmd.extend(['--serial', device_id])
    perfetto_process = subprocess.Popen(cmd,
                                        stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE)
    lines = []
    for line in perfetto_process.stdout:
        line = line.decode('utf-8')
        lines.append(line)
        if 'enabled ftrace' in line.strip():
            return perfetto_process, perfetto_trace_path
    raise ValueError(
        'Expected to find line containing: enabled ftrace, got: %s' % lines)


def stop_record_android_trace(perfetto_process, out_dir):
    if perfetto_process.poll() is not None:
        raise ValueError('Expected perfetto process to be running')
    # perfetto should terminate in at most 15 seconds,
    perfetto_config_duration = 15
    stdout, stderr = perfetto_process.communicate(
        timeout=perfetto_config_duration * 2)
    stdout = stdout.decode('utf-8')
    stderr = stderr.decode('utf-8')
    assert perfetto_process.returncode == 0
    assert os.path.exists(os.path.join(out_dir, 'trace.perfetto-trace'))


# https://perfetto.dev/docs/analysis/sql-tables
def find_slices_by_name(slice_name, options, trace_processor):
    return trace_processor.query(
        'SELECT slice.dur, slice.ts FROM slice'
        ' INNER JOIN thread_track ON (slice.track_id = thread_track.id)'
        ' INNER JOIN thread using (utid)'
        ' INNER JOIN process using (upid)'
        ' WHERE slice.name = "%s"'
        ' AND process.name = "%s"'
        ' ORDER BY slice.ts ASC' % (slice_name, options.app_id))


def find_unique_slice_by_name(slice_name, options, trace_processor):
    query_it = find_slices_by_name(slice_name, options, trace_processor)
    assert len(query_it) == 1
    return next(query_it)


def get_slice_end_since_start(slice, initial_slice):
    return (slice.ts + slice.dur - initial_slice.ts) / 1000000
