/*
 * Copyright (C) 2017 Genymobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.genymobile.gnirehtet;

import android.net.VpnService;
import android.util.Log;

import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * Expose a {@link Tunnel} that automatically handles {@link RelayTunnel} reconnections.
 */
public class PersistentRelayTunnel implements Tunnel {

    private static final String TAG = PersistentRelayTunnel.class.getSimpleName();

    private final RelayTunnelProvider provider;
    private boolean stopped;

    public PersistentRelayTunnel(VpnService vpnService) {
        provider = new RelayTunnelProvider(vpnService);
    }

    @Override
    public void send(byte[] packet, int len) throws IOException {
        while (!stopped) {
            try {
                Tunnel tunnel = provider.getCurrentTunnel();
                tunnel.send(packet, len);
                return;
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Cannot send to tunnel", e);
                provider.invalidateTunnel();
            }
        }
        throw new InterruptedIOException("Persistent tunnel stopped");
    }

    @Override
    public int receive(byte[] packet) throws IOException {
        while (!stopped) {
            try {
                Tunnel tunnel = provider.getCurrentTunnel();
                int r = tunnel.receive(packet);
                if (r == -1) {
                    Log.d(TAG, "Tunnel read EOF");
                    provider.invalidateTunnel();
                    continue;
                }
                return r;
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Cannot send to tunnel", e);
                provider.invalidateTunnel();
            }
        }
        throw new InterruptedIOException("Persistent tunnel stopped");
    }

    @Override
    public synchronized void close() {
        stopped = true;
        provider.invalidateTunnel();
    }
}
