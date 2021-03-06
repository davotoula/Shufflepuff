/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.mock;

import com.shuffle.bitcoin.EncryptionKey;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 *
 * Created by Daniel Krawisz on 12/8/15.
 */
public class MockEncryptionKey implements EncryptionKey, Serializable {
    public  int index;
    private static Pattern pattern = Pattern.compile("^ek\\[[0-9]+\\]$");

    public MockEncryptionKey(int index) {
        this.index = index;
    }

    public MockEncryptionKey(String str) throws NumberFormatException {

        if (!pattern.matcher(str).matches()) {
            throw new IllegalArgumentException();
        }

        index = Integer.parseInt(str.substring(3, str.length() - 1));

    }

    @Override
    public String encrypt(String m) {
        String decrypted = "~decrypt[" + index + "]";

        if (m.endsWith(decrypted)) {
            return m.substring(0, m.length() - decrypted.length());
        }

        return m + "~encrypt[" + index + "]";
    }

    @Override
    public String toString() {
        return "ek[" + index + "]";
    }

    @Override
    public int hashCode() {
        return index;
    }

    @Override
    public boolean equals(Object o) {
        return o != null
                && o instanceof MockEncryptionKey && index == ((MockEncryptionKey) o).index;
    }

}
