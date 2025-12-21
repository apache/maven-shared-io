/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.shared.io.location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.shared.io.logging.DefaultMessageHolder;
import org.apache.maven.shared.io.logging.MessageHolder;
import org.junit.jupiter.api.Test;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocatorTest {

    @Test
    void shouldConstructWithNoParams() {
        new Locator();
    }

    @Test
    void shouldConstructWithStrategyStackAndMessageHolder() {
        new Locator(Collections.<LocatorStrategy>emptyList(), new DefaultMessageHolder());
    }

    @Test
    void shouldAllowModificationOfStrategiesAfterConstructionWithUnmodifiableStack() {
        Locator locator = new Locator(Collections.emptyList(), new DefaultMessageHolder());

        locator.addStrategy(new FileLocatorStrategy());

        assertEquals(1, locator.getStrategies().size());
    }

    @Test
    void shouldRetrieveNonNullMessageHolderWhenConstructedWithoutParams() {
        assertNotNull(new Locator().getMessageHolder());
    }

    @Test
    void setStrategiesShouldClearAnyPreExistingStrategiesOut() {
        LocatorStrategy originalStrategy = createMock(LocatorStrategy.class);
        LocatorStrategy replacementStrategy = createMock(LocatorStrategy.class);

        replay(originalStrategy, replacementStrategy);

        Locator locator = new Locator();
        locator.addStrategy(originalStrategy);

        locator.setStrategies(Collections.singletonList(replacementStrategy));

        List<LocatorStrategy> strategies = locator.getStrategies();

        assertFalse(strategies.contains(originalStrategy));
        assertTrue(strategies.contains(replacementStrategy));

        verify(originalStrategy, replacementStrategy);
    }

    @Test
    void shouldRemovePreviouslyAddedStrategy() {
        LocatorStrategy originalStrategy = createMock(LocatorStrategy.class);

        replay(originalStrategy);

        Locator locator = new Locator();
        locator.addStrategy(originalStrategy);

        List<LocatorStrategy> strategies = locator.getStrategies();

        assertTrue(strategies.contains(originalStrategy));

        locator.removeStrategy(originalStrategy);

        strategies = locator.getStrategies();

        assertFalse(strategies.contains(originalStrategy));

        verify(originalStrategy);
    }

    @Test
    void resolutionFallsThroughStrategyStackAndReturnsNullIfNotResolved() {
        List<LocatorStrategy> strategies = new ArrayList<>();

        strategies.add(new LoggingLocatorStrategy());
        strategies.add(new LoggingLocatorStrategy());
        strategies.add(new LoggingLocatorStrategy());

        MessageHolder mh = new DefaultMessageHolder();

        Locator locator = new Locator(strategies, mh);

        Location location = locator.resolve("some-specification");

        assertNull(location);

        assertEquals(3, mh.size());
    }

    public static final class LoggingLocatorStrategy implements LocatorStrategy {

        static int instanceCounter = 0;

        int counter = instanceCounter++;

        public Location resolve(String locationSpecification, MessageHolder messageHolder) {
            messageHolder.addMessage("resolve hit on strategy-" + (counter));
            return null;
        }
    }
}
