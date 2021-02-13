/*
 * Copyright (C) 2021 Grakn Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package grakn.core.rocks;

import com.google.ortools.Loader;
import grakn.core.Grakn;
import grakn.core.common.exception.ErrorMessage;
import grakn.core.common.exception.GraknException;
import grakn.core.common.parameters.Arguments;
import grakn.core.common.parameters.Options;
import grakn.core.concurrent.common.Executors;
import org.rocksdb.RocksDB;
import org.rocksdb.UInt64AddOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static grakn.core.common.exception.ErrorMessage.Database.DATABASE_NOT_FOUND;
import static grakn.core.common.exception.ErrorMessage.Internal.GRAKN_CLOSED;

public class RocksGrakn implements Grakn {

    private static final Logger LOG = LoggerFactory.getLogger(RocksGrakn.class);
    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    static {
        RocksDB.loadLibrary();
        Loader.loadNativeLibraries();
        ErrorMessage.loadConstants();
    }

    private final Path directory;
    private final Options.Database graknDBOptions;
    private final org.rocksdb.Options rocksDBOptions;
    private final RocksDatabaseManager databaseMgr;
    private final AtomicBoolean isOpen;

    protected RocksGrakn(Path directory, Options.Database options, Factory.DatabaseManager databaseMgrFactory) {
        if (!Executors.isInitialised()) Executors.initialise(MAX_THREADS * 2, MAX_THREADS);
        this.directory = directory;
        this.graknDBOptions = options;
        this.rocksDBOptions = new org.rocksdb.Options()
                .setCreateIfMissing(true)
                .setMaxBackgroundJobs(MAX_THREADS / 2)
                .setMergeOperator(new UInt64AddOperator());
        this.databaseMgr = databaseMgrFactory.databaseManager(this);
        this.databaseMgr.loadAll();
        this.isOpen = new AtomicBoolean(true);
    }

    public static RocksGrakn open(Path directory) {
        return open(directory, new Options.Database(), new RocksFactory());
    }

    public static RocksGrakn open(Path directory, Factory graknFactory) {
        return open(directory, new Options.Database(), graknFactory);
    }

    public static RocksGrakn open(Path directory, Options.Database options, Factory graknFactory) {
        return graknFactory.grakn(directory, options);
    }

    public Path directory() {
        return directory;
    }

    org.rocksdb.Options rocksDBOptions() {
        return rocksDBOptions;
    }

    public Options.Database options() {
        return graknDBOptions;
    }

    @Override
    public RocksSession session(String database, Arguments.Session.Type type) {
        return session(database, type, new Options.Session());
    }

    @Override
    public RocksSession session(String database, Arguments.Session.Type type, Options.Session options) {
        if (!isOpen.get()) throw GraknException.of(GRAKN_CLOSED);
        if (databaseMgr.contains(database)) return databaseMgr.get(database).createAndOpenSession(type, options);
        else throw GraknException.of(DATABASE_NOT_FOUND, database);
    }

    @Override
    public RocksDatabaseManager databases() {
        return databaseMgr;
    }

    @Override
    public boolean isOpen() {
        return this.isOpen.get();
    }

    @Override
    public void close() {
        if (isOpen.compareAndSet(true, false)) {
            closeResources();
        }
    }

    /**
     * Responsible for committing the initial schema of a database.
     * A different implementation of this class may override it.
     */
    protected void closeResources() {
        databaseMgr.all().parallelStream().forEach(RocksDatabase::close);
        rocksDBOptions.close();
    }
}
