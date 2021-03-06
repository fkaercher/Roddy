/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config

import de.dkfz.roddy.tools.BufferValue
import de.dkfz.roddy.tools.TimeUnit
import groovy.transform.CompileStatic
import static de.dkfz.roddy.config.ResourceSetSize.*

/**
 * Test class for ToolEntry
 *
 * Fill in more tests when necessary.
 *
 * Created by heinold on 05.07.16.
 */
@CompileStatic
class ToolEntryTest extends GroovyTestCase {

    private ToolEntry createTestToolEntry() {
        def te = new ToolEntry("TestTool", "TestPath", "/SomewhereOverTheRainbow")
        te.getResourceSets().add(new ToolEntry.ResourceSet(ResourceSetSize.s, new BufferValue(1), 1, 1, new TimeUnit("01:00"), new BufferValue(1), "default", ""));
        te.getResourceSets().add(new ToolEntry.ResourceSet(ResourceSetSize.l, new BufferValue(1), 1, 1, new TimeUnit("01:00"), new BufferValue(1), "default", ""));
        return te;
    }

    private Configuration createTestConfiguration(final ResourceSetSize resourceSetSize) {
        return new Configuration(null) {
            @Override
            ResourceSetSize getResourcesSize() {
                return resourceSetSize;
            }
        }
    }

    void testHasResourceSets() {
        def entry = createTestToolEntry()
        assert entry.hasResourceSets()
        assert entry.getResourceSets().size() == 2
    }

    void testGetResourceSet() {
        def entry = createTestToolEntry()
        Map<ResourceSetSize, ResourceSetSize> givenAndExpected = [
                (t) : s,    // Select a set which is too small.
                (s) : s,    // Select a set which is available.
                (m) : l,    // Select a set which is in the middle and does not exist.
                (l) : l,    // Select a set which is available.
                (xl): l,    // Select a too large set.
        ].each {
            ResourceSetSize given, ResourceSetSize expected ->
                assert entry.getResourceSet(createTestConfiguration(given)).getSize() == expected
        }
    }
}
