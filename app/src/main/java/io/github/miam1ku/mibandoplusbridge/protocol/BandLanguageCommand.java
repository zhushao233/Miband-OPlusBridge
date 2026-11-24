// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.protocol;

import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;

/** Builds the observed type 2/subtype 6 language update used by Xiaomi wearables. */
public final class BandLanguageCommand {
    private BandLanguageCommand() {}

    public static XiaomiProto.Command setLanguage(String locale) {
        if (locale == null || locale.isBlank()) {
            throw new IllegalArgumentException("BAND_LANGUAGE_INVALID");
        }
        return XiaomiProto.Command.newBuilder().setType(2).setSubtype(6)
                .setSystem(XiaomiProto.System.newBuilder().setLanguage(
                        XiaomiProto.Language.newBuilder().setCode(locale)))
                .build();
    }
}
