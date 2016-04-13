/*
 * Copyright (c) 2016.
 *
 * Rex M. Torres <rexmtorres@gmail.com>
 */

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rexmtorres.android.patternlock;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for the lock pattern and its settings.
 */
public class PatternLockUtils {
    public static String patternStringToBaseZero(String pattern) {
        if (pattern == null) {
            return "";
        }
        final int patternSize = pattern.length();
        byte[] res = new byte[patternSize];
        final byte[] bytes = pattern.getBytes();
        for (int i = 0; i < patternSize; i++) {
            res[i] = (byte) (bytes[i] - '1');
        }
        return new String(res);
    }

    /**
     * Generate an SHA-1 hash for the pattern. Not the most secure, but it is
     * at least a second level of protection. First level is that the file
     * is in a location only readable by the system process.
     *
     * @param pattern the gesture pattern.
     *
     * @return the hash of the pattern in a byte array.
     */
    public static byte[] patternToHash(List<PatternLockView.Cell> pattern) {
        if (pattern == null) {
            return null;
        }
        final int patternSize = pattern.size();
        byte[] res = new byte[patternSize];
        for (int i = 0; i < patternSize; i++) {
            PatternLockView.Cell cell = pattern.get(i);
            res[i] = (byte) (cell.getRow() * 3 + cell.getColumn());
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(res);
            return hash;
        } catch (NoSuchAlgorithmException nsa) {
            return res;
        }
    }

    /**
     * Serialize a pattern.
     *
     * @param pattern The pattern.
     *
     * @return The pattern in string form.
     */
    public static String patternToString(List<PatternLockView.Cell> pattern) {
        if (pattern == null) {
            return "";
        }
        final int patternSize = pattern.size();
        byte[] res = new byte[patternSize];
        for (int i = 0; i < patternSize; i++) {
            PatternLockView.Cell cell = pattern.get(i);
            res[i] = (byte) (cell.getRow() * 3 + cell.getColumn() + '1');
        }
        return new String(res);
    }

    /**
     * Deserialize a pattern.
     *
     * @param string The pattern serialized with {@link #patternToString}
     *
     * @return The pattern.
     */
    public static List<PatternLockView.Cell> stringToPattern(String string) {
        if (string == null) {
            return null;
        }
        List<PatternLockView.Cell> result = new ArrayList<>();
        final byte[] bytes = string.getBytes();
        for (int i = 0; i < bytes.length; i++) {
            byte b = (byte) (bytes[i] - '1');
            result.add(PatternLockView.Cell.of(b / 3, b % 3));
        }
        return result;
    }
}
