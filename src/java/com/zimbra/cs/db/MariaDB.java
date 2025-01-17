/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2007, 2008, 2009, 2010, 2011, 2012, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.db;

import com.zimbra.common.localconfig.LC;


public class MariaDB extends MySQL {

    @Override
    DbPool.PoolConfig getPoolConfig() {
        return new MariaDBConfig();
    }

    protected class MariaDBConfig extends MySQLConfig {

        @Override
        protected String getDriverClassName() {
            return "org.mariadb.jdbc.Driver";
        }

        @Override
        protected String getRootUrl() {
            String bindAddress = LC.mysql_bind_address.value();
            if (bindAddress.indexOf(':') > -1) {
                bindAddress = "[" + bindAddress + "]";
            }

            return "jdbc:mysql://" + bindAddress + ":" + LC.mysql_port.value() + "/";
        }

    }
}
