/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2008, 2009, 2010, 2012, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.mailbox.util;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zimbra.cs.mailbox.MailItem;

public final class TypedIdList implements Iterable<Map.Entry<MailItem.Type, List<TypedIdList.ItemInfo>>> {
    public static class ItemInfo {
        int id;
        String uuid;
        int modSequence;

        public ItemInfo(int id, String uuid) {
            this.id = id;
            this.uuid = uuid;
        }

        public ItemInfo(int id, String uuid, int modSequence) {
            this(id, uuid);
            this.modSequence = modSequence;
        }

        public int getId() {
            return this.id;
        }

        public String getUuid() {
            return this.uuid;
        }

        public int getModSequence() {
            return this.modSequence;
        }
    }

    private final Map<MailItem.Type, List<ItemInfo>> type2ids = new EnumMap<MailItem.Type, List<ItemInfo>>(MailItem.Type.class);

    public TypedIdList() {
    }

    public TypedIdList(TypedIdList other) {
        this();
        addAll(other);
    }

    /** Adds an id/UUID pair to the list. */
    public void add(MailItem.Type type, Integer id, String uuid) {
        if (id == null)
            return;

        List<ItemInfo> items = type2ids.get(type);
        if (items == null) {
            type2ids.put(type, items = new ArrayList<ItemInfo>(1));
        }
        items.add(new ItemInfo(id, uuid));
    }

    public void add(MailItem.Type type, Integer id, String uuid, int modSequence) {
        if (id == null)
            return;

        List<ItemInfo> items = type2ids.get(type);
        if (items == null) {
            type2ids.put(type, items = new ArrayList<ItemInfo>(1));
        }
        items.add(new ItemInfo(id, uuid, modSequence));
    }

    /** Adds all contents of another TypedIdList to this TypedIdList. */
    public void addAll(TypedIdList other) {
        if (other == null || other.isEmpty())
            return;

        for (Map.Entry<MailItem.Type, List<ItemInfo>> row : other.type2ids.entrySet()) {
            MailItem.Type type = row.getKey();
            List<ItemInfo> items = type2ids.get(type);
            if (items == null) {
                type2ids.put(type, items = new ArrayList<ItemInfo>(row.getValue().size()));
            }
            items.addAll(row.getValue());
        }
    }

    /** Removes an entry from the TypedIdList, if present.  Items are specified
     *  by id.
     * @return whether the item was present before the remove. */
    public boolean remove(MailItem.Type type, Integer id) {
        if (id == null) {
            return false;
        }

        List<ItemInfo> items = type2ids.get(type);
        if (items != null) {
            for (Iterator<ItemInfo> it = items.iterator(); it.hasNext(); ) {
                if (id.equals(it.next().getId())) {
                    it.remove();
                    if (items.isEmpty()) {
                        type2ids.remove(type);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** Removes all items of a given type from the TypedIdList. */
    public void remove(MailItem.Type type) {
        type2ids.remove(type);
    }

    public boolean contains(Integer id) {
        for (List<ItemInfo> set : type2ids.values()) {
            for (ItemInfo entry : set) {
                if (id.equals(entry.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<MailItem.Type> types() {
        Set<MailItem.Type> types = type2ids.keySet();
        return types.isEmpty() ? EnumSet.noneOf(MailItem.Type.class) : EnumSet.copyOf(type2ids.keySet());
    }

    public List<Integer> getIds(Set<MailItem.Type> types) {
        List<Integer> ids = null, typedIds;
        for (MailItem.Type type : types) {
            if ((typedIds = getIds(type)) == null)
                continue;

            if (ids == null) {
                ids = new ArrayList<Integer>(typedIds.size());
            }
            ids.addAll(typedIds);
        }
        return ids;
    }

    public List<Integer> getIds(MailItem.Type type) {
        List<ItemInfo> items = type2ids.get(type);
        if (items == null) {
            return null;
        }

        List<Integer> ids = new ArrayList<Integer>(items.size());
        for (ItemInfo pair : items) {
            ids.add(pair.getId());
        }
        return ids;
    }

    public List<Integer> getAllIds() {
        List<Integer> ids = new ArrayList<Integer>();
        for (List<ItemInfo> set : type2ids.values()) {
            for (ItemInfo pair : set) {
                ids.add(pair.getId());
            }
        }
        return ids;
    }

    public int size() {
        int size = 0;
        for (List<ItemInfo> set : type2ids.values()) {
            size += set.size();
        }
        return size;
    }

    @Override
    public Iterator<Map.Entry<MailItem.Type, List<ItemInfo>>> iterator() {
        return type2ids.entrySet().iterator();
    }

    public boolean isEmpty() {
        return type2ids.isEmpty();
    }

    public void clear() {
        type2ids.clear();
    }

    @Override
    public String toString() {
        return type2ids.toString();
    }
}
