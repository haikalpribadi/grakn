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

package grakn.core.common.iterator;

import java.util.NoSuchElementException;
import java.util.function.Function;

class FlatMappedIterator<T, U> extends AbstractResourceIterator<U> {

    private final ResourceIterator<T> sourceIterator;
    private ResourceIterator<U> currentIterator;
    private final Function<T, ResourceIterator<U>> flatMappingFn;
    private State state;

    private enum State {INIT, ACTIVE, COMPLETED}

    public FlatMappedIterator(ResourceIterator<T> iterator, Function<T, ResourceIterator<U>> flatMappingFn) {
        this.sourceIterator = iterator;
        this.flatMappingFn = flatMappingFn;
        this.state = State.INIT;
    }

    @Override
    public boolean hasNext() {
        if (state == State.COMPLETED) {
            return false;
        } else if (state == State.INIT) {
            initialiseAndSetState();
            if (state == State.COMPLETED) return false;
        }

        return fetchAndCheck();
    }

    private boolean fetchAndCheck() {
        while (!currentIterator.hasNext() && sourceIterator.hasNext()) {
            currentIterator = flatMappingFn.apply(sourceIterator.next());
        }
        return currentIterator.hasNext();
    }

    private void initialiseAndSetState() {
        if (!sourceIterator.hasNext()) {
            state = State.COMPLETED;
        } else {
            currentIterator = flatMappingFn.apply(sourceIterator.next());
            state = State.ACTIVE;
        }
    }

    @Override
    public U next() {
        if (!hasNext()) throw new NoSuchElementException();
        return currentIterator.next();
    }

    @Override
    protected void recycleFn() {
        sourceIterator.recycle();
    }
}
