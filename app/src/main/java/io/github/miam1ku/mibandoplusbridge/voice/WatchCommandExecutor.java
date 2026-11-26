// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

/** Executes a locally recognised watch command and returns the text shown on the band. */
@FunctionalInterface
public interface WatchCommandExecutor {
    String execute(WatchCommand command) throws Exception;
}
