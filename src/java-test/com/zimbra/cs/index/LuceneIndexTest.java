/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2012, 2013, 2014 Zimbra, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.index;

/**
 * Unit test for {@link LuceneIndex}.
 */

public final class LuceneIndexTest extends AbstractIndexStoreTest {

    @Override
    protected String getIndexStoreFactory() {
        // Default for LC.zimbra_class_index_store_factory.value() is USUALLY this
        return "com.zimbra.cs.index.LuceneIndex$Factory";
    }
}
